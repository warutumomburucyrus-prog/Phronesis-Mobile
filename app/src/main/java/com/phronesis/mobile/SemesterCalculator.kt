package com.phronesis.mobile

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object SemesterCalculator {

    fun currentWeek(
        startDate: LocalDate,
        totalWeeks: Int,
        today: LocalDate = LocalDate.now()
    ): Int {
        val daysPassed = ChronoUnit.DAYS.between(startDate, today)
        val week = (daysPassed / 7).toInt() + 1
        return week.coerceIn(1, totalWeeks)
    }

    fun topicsForWeek(
        topics: List<String>,
        currentWeek: Int,
        examWeek: Int
    ): List<String> {
        if (topics.isEmpty() || examWeek < 1) return emptyList()
        if (currentWeek > examWeek) return emptyList()

        val perWeek = (topics.size + examWeek - 1) / examWeek
        val from = (currentWeek - 1) * perWeek
        if (from >= topics.size) return emptyList()
        val to = minOf(from + perWeek, topics.size)

        return topics.subList(from, to)
    }

    fun parseTopics(text: String): List<String> =
        text.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun bubbleText(unitName: String, topics: List<String>): String {
        if (topics.isEmpty()) return "Today's $unitName: time to revise"
        return "Today's $unitName: explore ${topics.joinToString(" and ")}"
    }
}
