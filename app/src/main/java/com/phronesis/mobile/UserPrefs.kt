package com.phronesis.mobile

import android.content.Context

private const val PREFS_NAME = "phronesis_prefs"
private const val KEY_USER_NAME = "user_name"
private const val KEY_PROGRAM = "user_program"
private const val KEY_UNIVERSITY = "user_university"

object UserPrefs {
    fun getName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun setName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getProgram(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PROGRAM, null)
    }

    fun setProgram(context: Context, program: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROGRAM, program).apply()
    }

    fun getUniversity(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UNIVERSITY, null)
    }

    fun setUniversity(context: Context, university: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_UNIVERSITY, university).apply()
    }
}