package com.phronesis.mobile

import android.content.Context

object QuizPrefs {
    private fun prefs(context: Context) =
        context.getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE)

    fun getQuestionCount(context: Context): Int = prefs(context).getInt("question_count", 5)

    fun setQuestionCount(context: Context, count: Int) {
        prefs(context).edit().putInt("question_count", count).apply()
    }
}