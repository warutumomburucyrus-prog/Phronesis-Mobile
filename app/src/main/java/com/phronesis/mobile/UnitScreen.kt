package com.phronesis.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun UnitsScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val units by db.unitDao().getAll().collectAsState(initial = emptyList())
    var editingUnit by remember { mutableStateOf<UnitEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your units", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            units.forEach { unit ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { editingUnit = unit },
                    shape = RoundedCornerShape(16.dp),
                    color = Clay.copy(alpha = 0.75f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(unit.code, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        if (unit.name.isNotBlank()) {
                            Text(unit.name, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (unit.topics.isNotBlank()) {
                            Text(unit.topics, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (units.isEmpty()) {
                Text("No units yet — import your timetable to add them automatically, or add one manually below.", style = MaterialTheme.typography.bodySmall)
            }
        }

        val hasUnnamedUnits = units.any { it.name.isBlank() }
        var isFillingNames by remember { mutableStateOf(false) }
        var fillNamesError by remember { mutableStateOf<String?>(null) }

        if (hasUnnamedUnits) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    val program = UserPrefs.getProgram(context)
                    if (!program.isNullOrBlank()) {
                        isFillingNames = true
                        fillNamesError = null
                        coroutineScope.launch {
                            try {
                                val codes = units.filter { it.name.isBlank() }.map { it.code }
                                val university = UserPrefs.getUniversity(context) ?: ""
                                val namesJson = GeminiHelper.identifyUnitNames(university, program, codes)
                                parseUnitNamesJson(namesJson).forEach { un ->
                                    db.unitDao().getByCode(un.code)?.let { existing ->
                                        db.unitDao().update(existing.copy(name = un.name))
                                    }
                                }
                            } catch (e: Exception) {
                                fillNamesError = e.message ?: "Couldn't fetch names right now."
                            } finally {
                                isFillingNames = false
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = Amber.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
            ) {
                Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (isFillingNames) "Fetching names..." else "Fill in unit names", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
            fillNamesError?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        val hasUnitsWithoutTopics = units.any { it.name.isNotBlank() && it.topics.isBlank() }
        var isFillingTopics by remember { mutableStateOf(false) }
        var fillTopicsError by remember { mutableStateOf<String?>(null) }

        if (hasUnitsWithoutTopics) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    val program = UserPrefs.getProgram(context)
                    if (!program.isNullOrBlank()) {
                        isFillingTopics = true
                        fillTopicsError = null
                        coroutineScope.launch {
                            try {
                                val university = UserPrefs.getUniversity(context) ?: ""
                                val targets = units.filter { it.name.isNotBlank() && it.topics.isBlank() }
                                targets.forEach { unit ->
                                    val topicsJson = GeminiHelper.identifyUnitTopics(university, program, unit.code, unit.name)
                                    val topics = parseUnitTopicsJson(topicsJson)
                                    db.unitDao().update(unit.copy(topics = topics.joinToString(", ")))
                                }
                            } catch (e: Exception) {
                                fillTopicsError = e.message ?: "Couldn't fetch topics right now."
                            } finally {
                                isFillingTopics = false
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = Clay.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
            ) {
                Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (isFillingTopics) "Fetching topics..." else "Fill in topics", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
            fillTopicsError?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().clickable { showAddDialog = true },
            shape = RoundedCornerShape(16.dp),
            color = Terracotta.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
        ) {
            Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("+ Add unit", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    if (showAddDialog || editingUnit != null) {
        UnitDialog(
            existing = editingUnit,
            onDismiss = { showAddDialog = false; editingUnit = null },
            onSave = { unit ->
                coroutineScope.launch {
                    if (unit.id == 0L) db.unitDao().insert(unit)
                    else db.unitDao().update(unit)
                }
                showAddDialog = false
                editingUnit = null
            },
            onDelete = { unit ->
                coroutineScope.launch { db.unitDao().delete(unit) }
                editingUnit = null
            }
        )
    }
}

@Composable
private fun UnitDialog(
    existing: UnitEntity?,
    onDismiss: () -> Unit,
    onSave: (UnitEntity) -> Unit,
    onDelete: (UnitEntity) -> Unit
) {
    var code by remember { mutableStateOf(existing?.code ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add unit" else "Edit unit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Unit code") })
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Unit name (optional)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (code.isNotBlank()) {
                    onSave(UnitEntity(id = existing?.id ?: 0, code = code, name = name))
                }
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