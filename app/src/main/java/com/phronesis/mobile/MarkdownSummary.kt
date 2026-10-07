package com.phronesis.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private fun inlineStyled(line: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        when {
            line.startsWith("**", i) -> {
                val end = line.indexOf("**", i + 2)
                if (end == -1) { append(line.substring(i)); i = line.length }
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            line[i] == '`' -> {
                val end = line.indexOf('`', i + 1)
                if (end == -1) { append(line[i]); i++ }
                else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Clay.copy(alpha = 0.35f))) {
                        append(line.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }
            line[i] == '*' -> {
                val end = line.indexOf('*', i + 1)
                if (end == -1) { i++ }
                else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(line.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            else -> { append(line[i]); i++ }
        }
    }
}

@Composable
fun MarkdownSummary(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            when {
                trimmed.isBlank() -> Spacer(Modifier.height(6.dp))
                trimmed.startsWith("#") -> Text(
                    text = inlineStyled(trimmed.trimStart('#').trim()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                )
                trimmed.startsWith("* ") || trimmed.startsWith("- ") -> Row(
                    Modifier.padding(start = (6 + indent * 4).dp, top = 3.dp)
                ) {
                    Text("•", modifier = Modifier.width(18.dp))
                    Text(
                        text = inlineStyled(trimmed.drop(2).trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> Text(
                    text = inlineStyled(trimmed),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

fun stripMarkdown(text: String): String =
    text.lines().joinToString("\n") { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        val cleaned = when {
            trimmed.startsWith("#") -> trimmed.trimStart('#').trim()
            trimmed.startsWith("* ") || trimmed.startsWith("- ") ->
                " ".repeat(indent) + "• " + trimmed.drop(2).trim()
            else -> line
        }
        cleaned.replace("**", "").replace("*", "").replace("`", "")
    }