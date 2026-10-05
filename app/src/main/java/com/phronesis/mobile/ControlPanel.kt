package com.phronesis.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.outlined.Close

@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseName: String,
    val courseCode: String,
    val topic: String,
    val modeOfSubmission: String,
    val deadline: String,
    val completed: Boolean = false
)

@Composable
fun ControlPanelScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val assignments by db.assignmentDao().getAll().collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }

    val units by db.unitDao().getAll().collectAsState(initial = emptyList())

    var showCompleted by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        val completedCount = assignments.count { it.completed }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (completedCount > 0) {
                Text(
                    if (showCompleted) "Hide completed ($completedCount)" else "Show completed ($completedCount)",
                    color = Terracotta,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { showCompleted = !showCompleted }
                )
            } else {
                Spacer(modifier = Modifier)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Terracotta, CircleShape)
                    .clickable { showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val visibleAssignments = if (showCompleted) assignments else assignments.filter { !it.completed }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            visibleAssignments.forEach { assignment ->
                AssignmentBubble(
                    assignment = assignment,
                    onToggleComplete = {
                        coroutineScope.launch {
                            db.assignmentDao().update(assignment.copy(completed = !assignment.completed))
                        }
                    },
                    onDelete = {
                        coroutineScope.launch {
                            db.assignmentDao().delete(assignment)
                        }
                    }
                )
            }
        }
    }

    if (showDialog) {
        AddAssignmentDialog(
            units = units,
            onDismiss = { showDialog = false },
            onSave = { newAssignment ->
                coroutineScope.launch {
                    db.assignmentDao().insert(newAssignment)
                }
                showDialog = false
            }
        )
    }
}

@Composable
private fun AssignmentBubble(assignment: Assignment, onToggleComplete: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Terracotta.copy(alpha = 0.75f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.4f))
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${assignment.courseName} (${assignment.courseCode})",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 60.dp)
                )
                Text("Topic: ${assignment.topic}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                Text("Submit via: ${assignment.modeOfSubmission}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                Text("Due: ${assignment.deadline}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (assignment.completed) Color.White else Color.White.copy(alpha = 0.25f),
                            CircleShape
                        )
                        .clickable(onClick = onToggleComplete),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✓",
                        color = if (assignment.completed) Terracotta else Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Outlined.Close,
                        contentDescription = "Delete assignment",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAssignmentDialog(units: List<UnitEntity>, onDismiss: () -> Unit, onSave: (Assignment) -> Unit) {
    var courseName by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }

    var nameExpanded by remember { mutableStateOf(false) }
    var topicExpanded by remember { mutableStateOf(false) }

    val nameSuggestions = remember(courseName, units) {
        if (courseName.isBlank()) units
        else units.filter {
            it.name.contains(courseName, ignoreCase = true) || it.code.contains(courseName, ignoreCase = true)
        }
    }

    val matchedUnit = remember(courseCode, units) { units.find { it.code == courseCode } }

    val availableTopics = remember(matchedUnit, units) {
        if (matchedUnit != null) {
            matchedUnit.topics.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            units.flatMap { u -> u.topics.split(",").map { it.trim() }.filter { it.isNotBlank() } }.distinct()
        }
    }

    val topicSuggestions = remember(topic, availableTopics) {
        if (topic.isBlank()) availableTopics
        else availableTopics.filter { it.contains(topic, ignoreCase = true) }
    }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add assignment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                ExposedDropdownMenuBox(expanded = nameExpanded, onExpandedChange = { nameExpanded = it }) {
                    OutlinedTextField(
                        value = courseName,
                        onValueChange = {
                            courseName = it
                            nameExpanded = true
                            val exactMatch = units.find { u -> u.name.equals(it, ignoreCase = true) }
                            courseCode = exactMatch?.code ?: courseCode
                        },
                        label = { Text("Course name") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = nameExpanded && nameSuggestions.isNotEmpty(), onDismissRequest = { nameExpanded = false }) {
                        nameSuggestions.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(if (unit.name.isNotBlank()) "${unit.name} (${unit.code})" else unit.code) },
                                onClick = {
                                    courseName = unit.name.ifBlank { unit.code }
                                    courseCode = unit.code
                                    topic = ""
                                    nameExpanded = false
                                }
                            )
                        }
                    }
                }

                var codeExpanded by remember { mutableStateOf(false) }
                val codeSuggestions = remember(courseCode, units) {
                    if (courseCode.isBlank()) units
                    else units.filter { it.code.contains(courseCode, ignoreCase = true) }
                }

                ExposedDropdownMenuBox(expanded = codeExpanded, onExpandedChange = { codeExpanded = it }) {
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = {
                            courseCode = it
                            codeExpanded = true
                            val exactMatch = units.find { u -> u.code.equals(it, ignoreCase = true) }
                            if (exactMatch != null) courseName = exactMatch.name.ifBlank { exactMatch.code }
                        },
                        label = { Text("Course code") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = codeExpanded && codeSuggestions.isNotEmpty(), onDismissRequest = { codeExpanded = false }) {
                        codeSuggestions.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(if (unit.name.isNotBlank()) "${unit.code} — ${unit.name}" else unit.code) },
                                onClick = {
                                    courseCode = unit.code
                                    courseName = unit.name.ifBlank { unit.code }
                                    topic = ""
                                    codeExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = topicExpanded, onExpandedChange = { topicExpanded = it }) {
                    OutlinedTextField(
                        value = topic,
                        onValueChange = {
                            topic = it
                            topicExpanded = true
                        },
                        label = { Text("Topic") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = topicExpanded && topicSuggestions.isNotEmpty(), onDismissRequest = { topicExpanded = false }) {
                        topicSuggestions.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    topic = t
                                    if (courseCode.isBlank()) {
                                        val owningUnit = units.find { u -> u.topics.split(",").map { s -> s.trim() }.contains(t) }
                                        if (owningUnit != null) {
                                            courseCode = owningUnit.code
                                            courseName = owningUnit.name.ifBlank { owningUnit.code }
                                        }
                                    }
                                    topicExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Mode of submission") })

                Box {
                    OutlinedTextField(
                        value = deadline,
                        onValueChange = {},
                        label = { Text("Deadline") },
                        readOnly = true,
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                val today = LocalDate.now()
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        deadline = LocalDate.of(year, month + 1, day)
                                            .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                                    },
                                    today.year, today.monthValue - 1, today.dayOfMonth
                                ).show()
                            }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    Assignment(
                        courseName = courseName,
                        courseCode = courseCode,
                        topic = topic,
                        modeOfSubmission = mode,
                        deadline = deadline
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}