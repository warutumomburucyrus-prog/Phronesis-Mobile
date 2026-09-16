package com.phronesis.mobile

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

private val sampleUnitNames = listOf("Calculus II (MATH201)", "Data Structures (CS301)", "Physics I (PHY110)")

private fun deriveQuizTitle(focus: String, unitName: String?): String {
    val topic = focus.trim().ifBlank { unitName?.substringBefore(" (") }
    return if (topic.isNullOrBlank()) "Quiz" else "${topic.replaceFirstChar { it.uppercase() }} Quiz"
}

private fun writeTextAsPdf(text: String, outputStream: java.io.OutputStream) {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val paint = Paint().apply { textSize = 12f }
    val lineHeight = paint.fontSpacing

    val lines = mutableListOf<String>()
    for (paragraph in text.split("\n")) {
        if (paragraph.isBlank()) {
            lines.add("")
            continue
        }
        var currentLine = StringBuilder()
        for (word in paragraph.split(" ")) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) > pageWidth - 2 * margin) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
    }

    var pageNumber = 1
    var lineIndex = 0
    while (lineIndex < lines.size) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        val canvas = page.canvas
        var y = margin + lineHeight
        while (lineIndex < lines.size && y < pageHeight - margin) {
            canvas.drawText(lines[lineIndex], margin, y, paint)
            y += lineHeight
            lineIndex++
        }
        document.finishPage(page)
        pageNumber++
    }

    document.writeTo(outputStream)
    document.close()
}

@Composable
fun QuizScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val notes by db.noteDao().getAll().collectAsState(initial = emptyList())
    var selectedNoteId by remember { mutableStateOf<Long?>(null) }
    val selectedNote = notes.find { it.id == selectedNoteId }

    var showGenerateDialog by remember { mutableStateOf(false) }
    var focusInput by remember { mutableStateOf("") }
    var noteMenuOpenFor by remember { mutableStateOf<Long?>(null) }
    var renamingNote by remember { mutableStateOf<NoteEntity?>(null) }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedQuiz by remember { mutableStateOf<List<QuizQuestion>?>(null) }
    var quizError by remember { mutableStateOf<String?>(null) }
    var quizTitle by remember { mutableStateOf("Quiz") }
    var showQuizView by remember { mutableStateOf(false) }

    var isSummarizing by remember { mutableStateOf(false) }
    var summaryText by remember { mutableStateOf<String?>(null) }
    var summaryError by remember { mutableStateOf<String?>(null) }
    var showSummaryView by remember { mutableStateOf(false) }
    var showSaveOptions by remember { mutableStateOf(false) }

    val txtSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null && summaryText != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(summaryText!!.toByteArray()) }
        }
        showSaveOptions = false
    }
    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null && summaryText != null) {
            context.contentResolver.openOutputStream(uri)?.use { writeTextAsPdf(summaryText!!, it) }
        }
        showSaveOptions = false
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "Untitled note"
            coroutineScope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val file = File(context.filesDir, "note_${System.currentTimeMillis()}.pdf")
                    file.writeBytes(bytes)
                    db.noteDao().insert(NoteEntity(name = name, filePath = file.absolutePath))
                }
            }
        }
    }

    if (showQuizView && generatedQuiz != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("←", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable { showQuizView = false })
                Text("✕", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable {
                    generatedQuiz = null
                    quizError = null
                    showQuizView = false
                    quizTitle = "Quiz"
                })
            }
            QuizPlayer(questions = generatedQuiz ?: emptyList())
        }
        return
    }

    if (showSummaryView && summaryText != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("←", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable { showSummaryView = false })
                Text("Save summary", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { showSaveOptions = !showSaveOptions })
                Text("✕", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable {
                    summaryText = null
                    summaryError = null
                    showSummaryView = false
                })
            }

            if (showSaveOptions) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f).clickable {
                            val name = selectedNote?.name?.substringBeforeLast(".") ?: "summary"
                            txtSaver.launch("$name.txt")
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Clay.copy(alpha = 0.5f)
                    ) {
                        Text("Save as .txt", modifier = Modifier.padding(10.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clickable {
                            val name = selectedNote?.name?.substringBeforeLast(".") ?: "summary"
                            pdfSaver.launch("$name.pdf")
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Clay.copy(alpha = 0.5f)
                    ) {
                        Text("Save as .pdf", modifier = Modifier.padding(10.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                SelectionContainer {
                    Text(summaryText ?: "", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(summaryText ?: ""))
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Clay.copy(alpha = 0.4f)
                ) {
                    Text("Copy", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Surface(
            modifier = Modifier.fillMaxWidth().clickable { pdfPicker.launch(arrayOf("application/pdf")) },
            shape = RoundedCornerShape(16.dp),
            color = Amber.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
        ) {
            Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Add notes", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }

        if (notes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notes.forEach { note ->
                    val isSelected = selectedNoteId == note.id
                    Box {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedNoteId = if (isSelected) null else note.id
                                summaryText = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Clay.copy(alpha = if (isSelected) 0.9f else 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(note.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "⋮",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.clickable { noteMenuOpenFor = note.id }
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = noteMenuOpenFor == note.id,
                            onDismissRequest = { noteMenuOpenFor = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { noteMenuOpenFor = null; renamingNote = note }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    noteMenuOpenFor = null
                                    coroutineScope.launch {
                                        File(note.filePath).delete()
                                        db.noteDao().delete(note)
                                        if (selectedNoteId == note.id) selectedNoteId = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = focusInput,
            onValueChange = { focusInput = it },
            label = { Text("What should this focus on? (optional)") },
            placeholder = { Text("e.g. derivatives in Calculus II") },
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedNote != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (summaryText != null) {
                        showSummaryView = true
                    } else {
                        isSummarizing = true
                        summaryError = null
                        coroutineScope.launch {
                            try {
                                summaryText = GeminiHelper.summarizeNotesPdf(selectedNote.filePath, focusInput)
                                showSummaryView = true
                            } catch (e: Exception) {
                                summaryError = e.stackTraceToString().take(400)
                            } finally {
                                isSummarizing = false
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = Clay.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
            ) {
                Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (summaryText != null) "See summary" else "Summarise notes",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            if (isSummarizing) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }

            summaryError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                if (generatedQuiz != null) {
                    showQuizView = true
                } else {
                    checkProAccess { hasPro ->
                        if (hasPro) {
                            showGenerateDialog = true
                        } else {
                            (context as? MainActivity)?.paywallLauncher?.launch()
                        }
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            color = Terracotta.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
        ) {
            Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (generatedQuiz != null) "View quiz" else "Generate quiz",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        if (isGenerating) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
            }
        }

        quizError?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showGenerateDialog) {
        GenerateQuizDialog(
            hasSelectedNote = selectedNote != null,
            onDismiss = { showGenerateDialog = false },
            onGenerate = { questionCount, unitName ->
                showGenerateDialog = false
                isGenerating = true
                quizError = null
                quizTitle = deriveQuizTitle(focusInput, unitName)
                coroutineScope.launch {
                    try {
                        val rawJson = if (selectedNote != null) {
                            GeminiHelper.generateQuizFromPdf(selectedNote.filePath, questionCount, focusInput)
                        } else {
                            GeminiHelper.generateQuizFromText(unitName ?: "General study material", questionCount, focusInput)
                        }
                        generatedQuiz = parseQuizJson(rawJson)
                        showQuizView = true
                    } catch (e: Exception) {
                        quizError = e.stackTraceToString().take(400)
                    } finally {
                        isGenerating = false
                    }
                }
            }
        )
    }

    renamingNote?.let { note ->
        var newName by remember(note.id) { mutableStateOf(note.name) }
        AlertDialog(
            onDismissRequest = { renamingNote = null },
            title = { Text("Rename note") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { db.noteDao().update(note.copy(name = newName)) }
                    renamingNote = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingNote = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GenerateQuizDialog(
    hasSelectedNote: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (questionCount: Int, unitName: String?) -> Unit
) {
    var questionCount by remember { mutableStateOf(5) }
    var selectedUnit by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate quiz") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Number of questions: $questionCount")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepperButton("−") { if (questionCount > 1) questionCount-- }
                    StepperButton("+") { if (questionCount < 20) questionCount++ }
                }
                if (!hasSelectedNote) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("No note selected — choose a unit instead:")
                    sampleUnitNames.forEach { unit ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { selectedUnit = unit },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedUnit == unit) Clay.copy(alpha = 0.8f) else Clay.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(unit, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canGenerate = hasSelectedNote || selectedUnit != null
            TextButton(onClick = { onGenerate(questionCount, selectedUnit) }, enabled = canGenerate) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).background(Terracotta, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun QuizPlayer(questions: List<QuizQuestion>) {
    var index by remember { mutableStateOf(0) }
    var selected by remember(index) { mutableStateOf<Int?>(null) }
    var score by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    if (finished) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Score: $score / ${questions.size}", style = MaterialTheme.typography.headlineSmall)
        }
        return
    }

    val q = questions[index]
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Question ${index + 1} of ${questions.size}", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Text(q.question, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        q.options.forEachIndexed { optIndex, option ->
            val isCorrectOption = optIndex == q.correctIndex
            val isPicked = selected == optIndex
            val bgColor = when {
                selected == null -> Clay.copy(alpha = 0.4f)
                isCorrectOption -> Color(0xFF4CAF50).copy(alpha = 0.7f)
                isPicked -> Color(0xFFE57373).copy(alpha = 0.7f)
                else -> Clay.copy(alpha = 0.25f)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(enabled = selected == null) {
                    selected = optIndex
                    if (isCorrectOption) score++
                },
                shape = RoundedCornerShape(12.dp),
                color = bgColor
            ) {
                Text(option, modifier = Modifier.padding(12.dp))
            }
        }

        if (selected != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = Clay.copy(alpha = 0.3f)) {
                Text(q.explanation, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (index < questions.size - 1) index++ else finished = true
                },
                shape = RoundedCornerShape(16.dp),
                color = Terracotta.copy(alpha = 0.75f)
            ) {
                Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (index < questions.size - 1) "Next" else "Finish", color = Color.White)
                }
            }
        }
    }
}