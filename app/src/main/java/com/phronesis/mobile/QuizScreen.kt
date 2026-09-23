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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
    val units by db.unitDao().getAll().collectAsState(initial = emptyList())

    var showGenerateDialog by remember { mutableStateOf(false) }
    var focusInput by remember { mutableStateOf("") }
    var noteMenuOpenFor by remember { mutableStateOf<Long?>(null) }
    var renamingNote by remember { mutableStateOf<NoteEntity?>(null) }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedQuiz by remember { mutableStateOf<List<QuizQuestion>?>(null) }
    var quizCurrentIndex by remember { mutableStateOf(0) }
    var quizAnswers by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) } // question index -> confirmed option index
    var quizFinished by remember { mutableStateOf(false) }
    var quizTimerTotalSeconds by remember { mutableStateOf<Int?>(null) }
    var quizTimerRemainingSeconds by remember { mutableStateOf<Int?>(null) }
    var quizError by remember { mutableStateOf<String?>(null) }
    var quizTitle by remember { mutableStateOf("Quiz") }
    var showQuizView by remember { mutableStateOf(false) }
    var quizUnitCode by remember { mutableStateOf<String?>(null) }
    var quizTopic by remember { mutableStateOf<String?>(null) }

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
            QuizPlayer(
                questions = generatedQuiz ?: emptyList(),
                currentIndex = quizCurrentIndex,
                onIndexChange = { quizCurrentIndex = it },
                answers = quizAnswers,
                onAnswerConfirmed = { qIdx, optIdx -> quizAnswers = quizAnswers + (qIdx to optIdx) },
                finished = quizFinished,
                onFinishedChange = { quizFinished = it },
                timerTotalSeconds = quizTimerTotalSeconds,
                timerRemainingSeconds = quizTimerRemainingSeconds,
                onTimerTick = { quizTimerRemainingSeconds = it },
                unitCode = quizUnitCode,
                topic = quizTopic,
                db = db,
                coroutineScope = coroutineScope
            )
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
            units = units,
            onDismiss = { showGenerateDialog = false },
            onGenerate = { questionCount, unitCode, topic, timerMinutes ->
                showGenerateDialog = false
                isGenerating = true
                quizError = null
                quizCurrentIndex = 0
                quizAnswers = emptyMap()
                quizFinished = false
                quizTimerTotalSeconds = timerMinutes?.times(60)
                quizTimerRemainingSeconds = timerMinutes?.times(60)
                val unitName = units.find { it.code == unitCode }?.name
                quizUnitCode = unitCode
                quizTopic = topic
                quizTitle = deriveQuizTitle(topic ?: focusInput, unitName)
                coroutineScope.launch {
                    try {
                        val topicOrFocus = topic ?: focusInput
                        val rawJson = if (selectedNote != null) {
                            GeminiHelper.generateQuizFromPdf(selectedNote.filePath, questionCount, topicOrFocus)
                        } else {
                            GeminiHelper.generateQuizFromText(unitName ?: "General study material", questionCount, topicOrFocus)
                        }
                        generatedQuiz = parseQuizJson(rawJson)
                        showQuizView = true
                        if (unitCode != null && topic != null) {
                            val existing = db.topicProgressDao().getOne(unitCode, topic)
                            val bumped = (existing?.familiarity ?: 0) + 15
                            if (existing != null) {
                                db.topicProgressDao().update(existing.copy(familiarity = bumped.coerceAtMost(100)))
                            } else {
                                db.topicProgressDao().insert(TopicProgressEntity(unitCode = unitCode, topic = topic, familiarity = 15))
                            }
                        }
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
    units: List<UnitEntity>,
    onDismiss: () -> Unit,
    onGenerate: (questionCount: Int, unitCode: String?, topic: String?, timerMinutes: Int?) -> Unit
) {
    var questionCount by remember { mutableStateOf(5) }
    var selectedUnit by remember { mutableStateOf<UnitEntity?>(null) }
    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var timerEnabled by remember { mutableStateOf(false) }
    var timerMinutes by remember { mutableStateOf(10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate quiz") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Number of questions: $questionCount")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepperButton("−") { if (questionCount > 1) questionCount-- }
                    StepperButton("+") { if (questionCount < 20) questionCount++ }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = timerEnabled, onCheckedChange = { timerEnabled = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Timed quiz")
                }
                if (timerEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Minutes: $timerMinutes")
                        StepperButton("−") { if (timerMinutes > 1) timerMinutes-- }
                        StepperButton("+") { if (timerMinutes < 120) timerMinutes++ }
                    }
                }

                if (!hasSelectedNote) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("No note selected — choose a unit instead:")
                    units.forEach { unit ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedUnit = unit
                                selectedTopic = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedUnit?.id == unit.id) Clay.copy(alpha = 0.8f) else Clay.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(
                                if (unit.name.isNotBlank()) "${unit.code} — ${unit.name}" else unit.code,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    val topicsForUnit = selectedUnit?.topics
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                    if (topicsForUnit.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Which topic?")
                        topicsForUnit.forEach { topic ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { selectedTopic = topic },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedTopic == topic) Terracotta.copy(alpha = 0.8f) else Terracotta.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text(topic, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canGenerate = hasSelectedNote || (selectedUnit != null && (selectedUnit?.topics?.isBlank() != false || selectedTopic != null))
            TextButton(
                onClick = { onGenerate(questionCount, selectedUnit?.code, selectedTopic, if (timerEnabled) timerMinutes else null) },
                enabled = canGenerate
            ) {
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
private fun QuizPlayer(
    questions: List<QuizQuestion>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    answers: Map<Int, Int>,
    onAnswerConfirmed: (Int, Int) -> Unit,
    finished: Boolean,
    onFinishedChange: (Boolean) -> Unit,
    timerTotalSeconds: Int?,
    timerRemainingSeconds: Int?,
    onTimerTick: (Int) -> Unit,
    unitCode: String? = null,
    topic: String? = null,
    db: AppDatabase? = null,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) {
    var hasSavedResult by remember { mutableStateOf(false) }

    LaunchedEffect(timerTotalSeconds, finished) {
        if (timerTotalSeconds != null && !finished) {
            var remaining = timerRemainingSeconds ?: timerTotalSeconds
            while (remaining > 0 && !finished) {
                kotlinx.coroutines.delay(1000)
                remaining--
                onTimerTick(remaining)
            }
            if (remaining <= 0) onFinishedChange(true)
        }
    }

    if (finished) {
        val score = answers.count { (qIdx, optIdx) -> questions.getOrNull(qIdx)?.correctIndex == optIdx }
        if (!hasSavedResult && unitCode != null && topic != null && db != null && coroutineScope != null) {
            hasSavedResult = true
            val percentThisAttempt = (score * 100) / questions.size
            coroutineScope.launch {
                val existing = db.topicProgressDao().getOne(unitCode, topic)
                if (existing != null) {
                    val newCount = existing.quizzesTaken + 1
                    val newMastery = ((existing.mastery * existing.quizzesTaken) + percentThisAttempt) / newCount
                    db.topicProgressDao().update(existing.copy(mastery = newMastery, quizzesTaken = newCount))
                } else {
                    db.topicProgressDao().insert(
                        TopicProgressEntity(unitCode = unitCode, topic = topic, mastery = percentThisAttempt, quizzesTaken = 1)
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Score: $score / ${questions.size}", style = MaterialTheme.typography.headlineSmall)
        }
        return
    }

    val q = questions[currentIndex]
    val alreadyConfirmed = answers.containsKey(currentIndex)
    var pendingSelection by remember(currentIndex) { mutableStateOf(answers[currentIndex]) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (timerRemainingSeconds != null) {
            val minutes = timerRemainingSeconds / 60
            val seconds = timerRemainingSeconds % 60
            Text(
                "⏱ %d:%02d".format(minutes, seconds),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (timerRemainingSeconds < 30) MaterialTheme.colorScheme.error else Color.Unspecified
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text("Question ${currentIndex + 1} of ${questions.size}", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Text(q.question, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            q.options.forEachIndexed { optIndex, option ->
                val isCorrectOption = optIndex == q.correctIndex
                val isPicked = pendingSelection == optIndex
                val bgColor = when {
                    !alreadyConfirmed -> if (isPicked) Clay.copy(alpha = 0.7f) else Clay.copy(alpha = 0.3f)
                    isCorrectOption -> Color(0xFF4CAF50).copy(alpha = 0.7f)
                    isPicked -> Color(0xFFE57373).copy(alpha = 0.7f)
                    else -> Clay.copy(alpha = 0.25f)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(enabled = !alreadyConfirmed) {
                        pendingSelection = optIndex
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor
                ) {
                    Text(option, modifier = Modifier.padding(12.dp))
                }
            }

            if (alreadyConfirmed) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = Clay.copy(alpha = 0.3f)) {
                    Text(q.explanation, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = currentIndex > 0) { onIndexChange(currentIndex - 1) },
                shape = RoundedCornerShape(16.dp),
                color = if (currentIndex > 0) Clay.copy(alpha = 0.6f) else Clay.copy(alpha = 0.2f)
            ) {
                Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Previous", color = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = alreadyConfirmed || pendingSelection != null) {
                        if (!alreadyConfirmed) {
                            onAnswerConfirmed(currentIndex, pendingSelection!!)
                        } else if (currentIndex < questions.size - 1) {
                            onIndexChange(currentIndex + 1)
                        } else {
                            onFinishedChange(true)
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                color = Terracotta.copy(alpha = if (alreadyConfirmed || pendingSelection != null) 0.75f else 0.3f)
            ) {
                Box(modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            !alreadyConfirmed -> "Confirm"
                            currentIndex < questions.size - 1 -> "Next"
                            else -> "Finish"
                        },
                        color = Color.White
                    )
                }
            }
        }
    }
}