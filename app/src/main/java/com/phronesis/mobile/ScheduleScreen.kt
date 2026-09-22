package com.phronesis.mobile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val units by db.unitDao().getAll().collectAsState(initial = emptyList())

    val coroutineScope = rememberCoroutineScope()

    val sessions by db.classSessionDao().getAll().collectAsState(initial = emptyList())
    val grouped = sessions.groupBy { it.day }.toSortedMap(compareBy { dayOrder.indexOf(it) })

    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var editingSession by remember { mutableStateOf<ClassSessionEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val timetablePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val program = UserPrefs.getProgram(context)
            if (program.isNullOrBlank()) {
                importError = "No program set — please set your program first."
                return@rememberLauncherForActivityResult
            }
            isImporting = true
            importError = null
            coroutineScope.launch {
                try {
                    val rawJson = GeminiHelper.parseTimetablePdf(context, uri, program)
                    val parsed = parseTimetableJson(rawJson)
                    db.classSessionDao().clearAll()
                    parsed.forEach { session ->
                        db.classSessionDao().insert(
                            ClassSessionEntity(
                                day = session.day,
                                unitCode = session.unitCode,
                                startTime = session.startTime,
                                endTime = session.endTime,
                                venue = session.venue
                            )
                        )
                    }

                    try {
                        val distinctCodes = parsed.map { it.unitCode }.distinct()
                        val university = UserPrefs.getUniversity(context) ?: ""
                        val namesJson = GeminiHelper.identifyUnitNames(university, program, distinctCodes)
                        parseUnitNamesJson(namesJson).forEach { un ->
                            val existing = db.unitDao().getByCode(un.code)
                            if (existing == null) {
                                db.unitDao().insert(UnitEntity(code = un.code, name = un.name))
                            } else if (existing.name.isBlank()) {
                                db.unitDao().update(existing.copy(name = un.name))
                            }
                        }
                    } catch (e: Exception) {
                        // Unit names just won't be filled in this time — not worth failing the whole import over.
                    }
                    if (parsed.isEmpty()) {
                        importError = "No classes found for \"$program\" — check the program name matches the timetable."
                    }
                } catch (e: Exception) {
                    importError = e.message ?: "Something went wrong reading the timetable."
                } finally {
                    isImporting = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("This week", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            grouped.forEach { (day, classes) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Amber.copy(alpha = 0.75f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(day, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        classes.sortedBy { it.startTime }.forEach { session ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editingSession = session },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val unitName = units.find { it.code == session.unitCode }?.name?.takeIf { it.isNotBlank() }
                                val label = if (unitName != null) "${session.unitCode} — $unitName" else session.unitCode
                                Text(
                                    "$label\n${session.startTime} to ${session.endTime} (${session.venue})",
                                    color = Color.White.copy(alpha = 0.95f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (sessions.isEmpty()) {
                Text("No classes yet — import your timetable below, or add one manually.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isImporting) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        importError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier.weight(1f).clickable { timetablePicker.launch(arrayOf("application/pdf")) },
                shape = RoundedCornerShape(16.dp),
                color = Terracotta.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
            ) {
                Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Import timetable PDF", color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Surface(
                modifier = Modifier.weight(1f).clickable { showAddDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = Clay.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
            ) {
                Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("+ Add class", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }

    if (showAddDialog || editingSession != null) {
        ClassSessionDialog(
            existing = editingSession,
            onDismiss = { showAddDialog = false; editingSession = null },
            onSave = { session ->
                coroutineScope.launch {
                    if (session.id == 0L) db.classSessionDao().insert(session)
                    else db.classSessionDao().update(session)
                }
                showAddDialog = false
                editingSession = null
            },
            onDelete = { session ->
                coroutineScope.launch { db.classSessionDao().delete(session) }
                editingSession = null
            }
        )
    }
}

@Composable
private fun ClassSessionDialog(
    existing: ClassSessionEntity?,
    onDismiss: () -> Unit,
    onSave: (ClassSessionEntity) -> Unit,
    onDelete: (ClassSessionEntity) -> Unit
) {
    var day by remember { mutableStateOf(existing?.day ?: "Monday") }
    var unitCode by remember { mutableStateOf(existing?.unitCode ?: "") }
    var startTime by remember { mutableStateOf(existing?.startTime ?: "") }
    var endTime by remember { mutableStateOf(existing?.endTime ?: "") }
    var venue by remember { mutableStateOf(existing?.venue ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add class" else "Edit class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("Day") })
                OutlinedTextField(value = unitCode, onValueChange = { unitCode = it }, label = { Text("Unit code") })
                OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start time") })
                OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End time") })
                OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("Venue") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ClassSessionEntity(
                        id = existing?.id ?: 0,
                        day = day, unitCode = unitCode, startTime = startTime, endTime = endTime, venue = venue
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { onDelete(existing) }) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}