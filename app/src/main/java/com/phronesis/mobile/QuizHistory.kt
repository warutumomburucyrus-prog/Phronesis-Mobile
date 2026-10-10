package com.phronesis.mobile

import android.content.Context
import org.json.JSONArray

object QuizHistory {
    private const val MAX_KEPT = 30

    private fun prefs(context: Context) =
        context.getSharedPreferences("quiz_history", Context.MODE_PRIVATE)

    fun getPast(context: Context, key: String): List<String> {
        val saved = prefs(context).getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(saved)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addQuestions(context: Context, key: String, newOnes: List<String>) {
        val all = (getPast(context, key) + newOnes).takeLast(MAX_KEPT)
        val arr = JSONArray()
        all.forEach { arr.put(it) }
        prefs(context).edit().putString(key, arr.toString()).apply()
    }
}