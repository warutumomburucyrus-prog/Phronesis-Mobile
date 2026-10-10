package com.phronesis.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterSetupDialog(
    initialStart: LocalDate?,
    initialWeeks: Int,
    initialExamWeek: Int,
    onSave: (LocalDate, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var startDate by remember { mutableStateOf(initialStart) }
    var weeksText by remember { mutableStateOf(initialWeeks.toString()) }
    var examText by remember { mutableStateOf(initialExamWeek.toString()) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        startDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = pickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up your semester") },
        text = {
            Column {
                OutlinedButton(onClick = { showPicker = true }) {
                    Text(startDate?.toString() ?: "Pick start date")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = weeksText,
                    onValueChange = { weeksText = it.filter(Char::isDigit) },
                    label = { Text("Total weeks") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = examText,
                    onValueChange = { examText = it.filter(Char::isDigit) },
                    label = { Text("Exam week") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val weeks = weeksText.toIntOrNull()
                val exam = examText.toIntOrNull()
                val start = startDate
                if (start != null && weeks != null && exam != null && weeks > 0 && exam in 1..weeks) {
                    onSave(start, weeks, exam)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}