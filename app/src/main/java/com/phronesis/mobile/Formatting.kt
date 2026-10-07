package com.phronesis.mobile

fun formatUnitCode(raw: String): String {
    val letters = raw.filter { it.isLetter() }.take(3).uppercase()
    if (letters.length < 3) return letters
    val digits = raw.filter { it.isDigit() }.take(4)
    return if (digits.isEmpty()) letters else "$letters $digits"
}

fun tidyImportedCode(raw: String): String {
    val formatted = formatUnitCode(raw)
    return if (Regex("[A-Z]{3} \\d{1,4}").matches(formatted)) formatted else raw.trim()
}