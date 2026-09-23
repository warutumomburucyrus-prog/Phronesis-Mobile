package com.phronesis.mobile

import android.inputmethodservice.Keyboard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun DashboardScreen(onBubbleClick: (String) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var userName by remember { mutableStateOf(UserPrefs.getName(context)) }
    var showNameDialog by remember { mutableStateOf(userName == null) }
    var showFamiliarityPopup by remember { mutableStateOf(false) }
    var showMasteryPopup by remember { mutableStateOf(false) }

    val sessions by db.classSessionDao().getAll().collectAsState(initial = emptyList())
    val units by db.unitDao().getAll().collectAsState(initial = emptyList())
    val assignments by db.assignmentDao().getAll().collectAsState(initial = emptyList())
    val topicProgress by db.topicProgressDao().getAll().collectAsState(initial = emptyList())

    val todaysClassesText = remember(sessions, units) {
        val todayName = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val now = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

        val upcoming = sessions
            .filter { it.day.equals(todayName, ignoreCase = true) }
            .mapNotNull { s ->
                try {
                    val end = LocalTime.parse(s.endTime.uppercase(Locale.ENGLISH), formatter)
                    if (end.isAfter(now)) s to end else null
                } catch (e: Exception) { null }
            }
            .sortedBy { it.second }
            .map { it.first }

        if (upcoming.isEmpty()) {
            "No more classes today"
        } else {
            upcoming.joinToString("\n") { s ->
                val name = units.find { it.code == s.unitCode }?.name?.takeIf { it.isNotBlank() }
                val label = if (name != null) "${s.unitCode} — $name" else s.unitCode
                "$label\n${s.startTime}–${s.endTime} (${s.venue})"
            }
        }
    }

    val assignmentsText = remember(assignments) {
        val pending = assignments.filter { !it.completed }
        if (pending.isEmpty()) "No assignments due" else
            pending.take(4).joinToString("\n") { "${it.courseName} — ${it.deadline}" }
    }

    val unitsText = remember(units) {
        if (units.isEmpty()) "No units yet — import your timetable"
        else units.joinToString("\n\n") {
            val header = if (it.name.isNotBlank()) "${it.code} — ${it.name}" else it.code
            if (it.topics.isNotBlank()) "$header\n${it.topics}" else header
        }
    }

    if (showNameDialog) {
        var nameInput by remember { mutableStateOf("") }
        var programInput by remember { mutableStateOf("") }
        var universityInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Let's set up your account") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your name") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = universityInput, onValueChange = { universityInput = it}, label = { Text("Your university (e.g. MUST)")},
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = programInput, onValueChange = { programInput = it }, label = { Text("Your program (e.g. BDS Y2S1)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nameInput.isNotBlank() && programInput.isNotBlank()) {
                        UserPrefs.setName(context, nameInput)
                        UserPrefs.setUniversity(context, universityInput)
                        UserPrefs.setProgram(context, programInput)
                        userName = nameInput
                        showNameDialog = false
                    }
                }) { Text("Save") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(2.5f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("Good to see you, ${userName ?: ""} ", style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            modifier = Modifier.weight(3f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Bubble("Today's Classes", Amber, todaysClassesText, Modifier.weight(1f)) { onBubbleClick(Routes.SCHEDULE) }
                Bubble("Assignments", Terracotta, assignmentsText, Modifier.weight(1f)) { onBubbleClick(Routes.CONTROL_PANEL) }
            }
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Bubble("Units Progress", Clay, unitsText, Modifier.weight(1f)) { showFamiliarityPopup = true }
                Bubble("Performance", Color(0xFFD98324), "No quiz results yet", Modifier.weight(1f)) { showMasteryPopup = true }
            }
        }
    }

    if (showFamiliarityPopup) {
        val familiarityItems = topicProgress.map { it ->
            val unit = units.find { u -> u.code == it.unitCode }
            val label = if (unit != null) "${it.unitCode} — ${it.topic}" else "${it.unitCode}: ${it.topic}"
            label to it.familiarity
        }
        ProgressPopup(
            title = "Familiarity by topic",
            ringColor = Clay,
            items = familiarityItems,
            onDismiss = { showFamiliarityPopup = false }
        )
    }

    if (showMasteryPopup) {
        val masteryItems = topicProgress
            .filter { it.quizzesTaken > 0 }
            .map { "${it.unitCode} — ${it.topic}" to it.mastery }
        ProgressPopup(
            title = "Mastery by topic",
            ringColor = Color(0xFFD98324),
            items = masteryItems,
            onDismiss = { showMasteryPopup = false }
        )
    }
}

@Composable
private fun Bubble(
    title: String,
    color: Color,
    content: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(color.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.14f))
                    .padding(10.dp)
            ) {
                Text(content, color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TopicProgressRow(label: String, percent: Int, ringColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxSize(),
                color = ringColor,
                trackColor = ringColor.copy(alpha = 0.2f),
                strokeWidth = 5.dp
            )
            Text("$percent%", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProgressPopup(
    title: String,
    ringColor: Color,
    items: List<Pair<String, Int>>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text("No topics yet — fetch topics for a unit first.")
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())
                ) {
                    items.forEach { (label, percent) ->
                        TopicProgressRow(label = label, percent = percent, ringColor = ringColor)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}