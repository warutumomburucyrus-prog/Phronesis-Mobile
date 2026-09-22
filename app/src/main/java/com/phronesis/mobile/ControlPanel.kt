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

    // Automatically updates whenever the database changes — no manual refresh needed.
    val assignments by db.assignmentDao().getAll().collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Box(
            modifier = Modifier
                .align(Alignment.End)
                .size(48.dp)
                .background(Terracotta, CircleShape)
                .clickable { showDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            assignments.forEach { assignment ->
                AssignmentBubble(
                    assignment = assignment,
                    onToggleComplete = {
                        coroutineScope.launch {
                            db.assignmentDao().update(assignment.copy(completed = !assignment.completed))
                        }
                    }
                )
            }
        }
    }

    if (showDialog) {
        AddAssignmentDialog(
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
private fun AssignmentBubble(assignment: Assignment, onToggleComplete: () -> Unit) {
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
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Topic: ${assignment.topic}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                Text("Submit via: ${assignment.modeOfSubmission}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                Text("Due: ${assignment.deadline}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
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
        }
    }
}

@Composable
private fun AddAssignmentDialog(onDismiss: () -> Unit, onSave: (Assignment) -> Unit) {
    var courseName by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add assignment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = courseName, onValueChange = { courseName = it }, label = { Text("Course name") })
                OutlinedTextField(value = courseCode, onValueChange = { courseCode = it }, label = { Text("Course code") })
                OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic") })
                OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Mode of submission") })
                val calendarContext = LocalContext.current
                OutlinedTextField(
                    value = deadline,
                    onValueChange = {},
                    label = { Text("Deadline") },
                    readOnly = true,
                    modifier = Modifier.clickable {
                        val today = LocalDate.now()
                        android.app.DatePickerDialog(
                            calendarContext,
                            { _, year, month, day ->
                                deadline = LocalDate.of(year, month + 1, day)
                                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                            },
                            today.year, today.monthValue - 1, today.dayOfMonth
                        ).show()
                    }
                )
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