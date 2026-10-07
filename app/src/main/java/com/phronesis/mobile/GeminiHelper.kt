package com.phronesis.mobile

import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object GeminiHelper {
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-3.1-flash-lite")
    }

    private suspend fun refreshAppCheckToken() = suspendCancellableCoroutine<Unit> { cont ->
        FirebaseAppCheck.getInstance().getAppCheckToken(true)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private suspend fun <T> withTokenRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            when {
                e.message?.contains("App Check", ignoreCase = true) == true -> {
                    refreshAppCheckToken()
                    block()
                }
                e.message?.contains("429", ignoreCase = true) == true ||
                        e.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ||
                        e.message?.contains("quota", ignoreCase = true) == true -> {
                    kotlinx.coroutines.delay(15000) // wait 15s, then retry once
                    block()
                }
                else -> throw e
            }
        }
    }

    private fun readPdfBytes(filePath: String): ByteArray = File(filePath).readBytes()

    private val jsonInstruction = """
        Output ONLY valid JSON, no markdown, no code fences, no extra text, in exactly this shape:
        [{"question":"...", "options":["...","...","...","..."], "correctIndex":0, "explanation":"..."}]
        correctIndex is the 0-based index of the correct option. The explanation should say why the
        correct answer is right and briefly why the others are wrong.
    """.trimIndent()

    suspend fun summarizeNotesPdf(filePath: String, focus: String = ""): String = withTokenRetry {
        val bytes = readPdfBytes(filePath)
        val focusLine = if (focus.isNotBlank()) " Focus specifically on: $focus." else ""
        val prompt = content {
            inlineData(bytes, "application/pdf")
            text("Summarize this document clearly and concisely.$focusLine Keep it factual and easy to review before an exam. Format with Markdown using only: '### ' headings, '- ' bullet points (one level, no nested bullets), and **bold** for key terms. No tables, no code blocks, no horizontal lines.")
        }
        model.generateContent(prompt).text ?: "No summary generated."
    }

    suspend fun generateQuizFromPdf(filePath: String, questionCount: Int, focus: String = ""): String = withTokenRetry {
        val bytes = readPdfBytes(filePath)
        val focusLine = if (focus.isNotBlank()) " Focus specifically on: $focus." else ""
        val prompt = content {
            inlineData(bytes, "application/pdf")
            text("Based on this document, generate exactly $questionCount multiple-choice quiz questions.$focusLine\n$jsonInstruction")
        }
        model.generateContent(prompt).text ?: "[]"
    }

    suspend fun generateQuizFromText(sourceText: String, questionCount: Int, focus: String = ""): String = withTokenRetry {
        val focusLine = if (focus.isNotBlank()) " Focus specifically on: $focus." else ""
        val prompt = "Based on the topic \"$sourceText\", generate exactly $questionCount multiple-choice quiz questions.$focusLine\n$jsonInstruction"
        model.generateContent(prompt).text ?: "[]"
    }

    suspend fun identifyUnitNames(university: String, program: String, codes: List<String>): String = withTokenRetry {
        val codesList = codes.joinToString(", ")
        val prompt = """
        At $university, in the program "$program", identify the full academic course title
        for each of these unit codes: $codesList.
        Use your knowledge of this specific university's curriculum if you have it. If you
        don't have specific knowledge of this university's course catalog, say so honestly by
        returning an empty string "" for that code's name rather than inventing a guess.
        Output ONLY valid JSON, no markdown, no code fences, in exactly this shape:
        [{"code":"CIT 3203","name":"..."}]
    """.trimIndent()
        model.generateContent(prompt).text ?: "[]"
    }

    suspend fun identifyUnitTopics(university: String, program: String, code: String, unitName: String): String = withTokenRetry {
        val prompt = """
        At $university, in the program "$program", list the main topics typically covered
        in the course "$code — $unitName".
        Use your knowledge of this specific university's curriculum if you have it. If you
        don't have specific knowledge, give a reasonable general breakdown based on the course title.
        Output ONLY valid JSON, no markdown, no code fences, in exactly this shape:
        {"topics":["Topic one","Topic two","Topic three"]}
    """.trimIndent()
        model.generateContent(prompt).text ?: """{"topics":[]}"""
    }

    suspend fun parseTimetablePdf(context: Context, uri: Uri, program: String): String = withTokenRetry {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Couldn't read the timetable PDF.")
        val prompt = content {
            inlineData(bytes, "application/pdf")
            text("""
            This document is a university teaching timetable. It may be laid out in any format —
            a grid/table, a day-by-day list, a column-per-day layout, or something else entirely.
            It may cover one program or many programs together.

            Carefully read the ENTIRE document, including any text that is rotated, in small print,
            in footnotes, or spread across multiple pages or columns. Identify every class session
            that belongs specifically to the program "$program" (match flexibly — the program name
            in the document might be abbreviated, reordered, or formatted differently than exactly
            "$program", so use your judgment to find the right match).

            For each class session belonging to that program, extract:
            - day: the full day name (e.g. "Monday")
            - unitCode: the unit/course code exactly as written (e.g. "CIT 3103")
            - startTime and endTime: in a consistent "h:mm AM/PM" format, converting from 24-hour
              time if needed
            - venue: the room/venue code

            Combine consecutive time blocks for the same unit on the same day into a single entry
            with the full start/end time range, rather than listing them separately.

            Output ONLY valid JSON, no markdown, no code fences, no extra commentary, in exactly
            this shape:
            [{"day":"Monday","unitCode":"CIT 3103","startTime":"7:00 AM","endTime":"10:00 AM","venue":"TB 17"}]

            If you cannot find the program "$program" anywhere in the document after a thorough
            search, return an empty array [].
        """.trimIndent())
        }
        model.generateContent(prompt).text ?: "[]"
    }
}