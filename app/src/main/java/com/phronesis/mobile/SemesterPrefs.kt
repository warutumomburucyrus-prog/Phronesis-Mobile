package com.phronesis.mobile

import android.content.Context
import java.time.LocalDate

object SemesterPrefs {
    private const val FILE = "semester_prefs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getStartDate(context: Context): LocalDate? =
        prefs(context).getString("start_date", null)?.let { LocalDate.parse(it) }

    fun setStartDate(context: Context, date: LocalDate) {
        prefs(context).edit().putString("start_date", date.toString()).apply()
    }

    fun getTotalWeeks(context: Context): Int = prefs(context).getInt("total_weeks", 14)

    fun setTotalWeeks(context: Context, weeks: Int) {
        prefs(context).edit().putInt("total_weeks", weeks).apply()
    }

    fun getExamWeek(context: Context): Int = prefs(context).getInt("exam_week", 14)

    fun setExamWeek(context: Context, week: Int) {
        prefs(context).edit().putInt("exam_week", week).apply()
    }
}