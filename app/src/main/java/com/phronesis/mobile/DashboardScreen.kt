package com.phronesis.mobile

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

    val familiarityPercent = remember(topicProgress) {
        if (topicProgress.isEmpty()) null else Math.round(topicProgress.map { it.familiarity }.average()).toInt()
    }
    val familiarityLabel = if (units.isEmpty()) "No units yet" else "across ${units.size} unit${if (units.size == 1) "" else "s"}"

    val masteryPercent = remember(topicProgress) {
        val quizzed = topicProgress.filter { it.quizzesTaken > 0 }
        if (quizzed.isEmpty()) null else Math.round(quizzed.map { it.mastery }.average()).toInt()
    }
    val masteryLabel = remember(topicProgress) {
        val quizzed = topicProgress.filter { it.quizzesTaken > 0 }
        if (quizzed.isEmpty()) "No quiz results yet" else "across ${quizzed.map { it.unitCode }.distinct().size} unit(s)"
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
                Bubble("Units Progress", Clay, familiarityLabel, Modifier.weight(1f), percent = familiarityPercent) { showFamiliarityPopup = true }
                Bubble("Performance", Color(0xFFD98324), masteryLabel, Modifier.weight(1f), percent = masteryPercent) { showMasteryPopup = true }
            }
        }
    }

    if (showFamiliarityPopup) {
        val familiarityByUnit = topicProgress
            .groupBy { it.unitCode }
            .map { (unitCode, entries) ->
                val unit = units.find { it.code == unitCode }
                val label = if (unit != null && unit.name.isNotBlank()) "$unitCode — ${unit.name}" else unitCode
                val avg = Math.round(entries.map { it.familiarity }.average()).toInt()
                label to avg
            }
        ProgressPopup(
            title = "Familiarity by unit",
            ringColor = Clay,
            items = familiarityByUnit,
            onDismiss = { showFamiliarityPopup = false }
        )
    }

    if (showMasteryPopup) {
        val masteryByUnit = topicProgress
            .filter { it.quizzesTaken > 0 }
            .groupBy { it.unitCode }
            .map { (unitCode, entries) ->
                val unit = units.find { it.code == unitCode }
                val label = if (unit != null && unit.name.isNotBlank()) "$unitCode — ${unit.name}" else unitCode
                val avg = Math.round(entries.map { it.mastery }.average()).toInt()
                label to avg
            }
        ProgressPopup(
            title = "Performance by unit",
            ringColor = Color(0xFFD98324),
            items = masteryByUnit,
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
    percent: Int? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
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
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (percent != null) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxSize(),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.25f),
                                strokeWidth = 4.dp
                            )
                            Text("$percent%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(content, color = Color.White, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
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