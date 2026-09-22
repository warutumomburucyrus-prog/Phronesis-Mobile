package com.phronesis.mobile

import org.json.JSONArray

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class ClassSessionData(
    val day: String,
    val unitCode: String,
    val startTime: String,
    val endTime: String,
    val venue: String
)

fun parseQuizJson(raw: String): List<QuizQuestion> {
    val cleaned = raw.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```").trim()
    val arr = JSONArray(cleaned)
    return (0 until arr.length()).map { i ->
        val obj = arr.getJSONObject(i)
        val optsArr = obj.getJSONArray("options")
        QuizQuestion(
            question = obj.getString("question"),
            options = (0 until optsArr.length()).map { optsArr.getString(it) },
            correctIndex = obj.getInt("correctIndex"),
            explanation = obj.getString("explanation")
        )
    }
}

fun parseTimetableJson(raw: String): List<ClassSessionData> {
    val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val arr = JSONArray(cleaned)
    return (0 until arr.length()).map { i ->
        val obj = arr.getJSONObject(i)
        ClassSessionData(
            day = obj.getString("day"),
            unitCode = obj.getString("unitCode"),
            startTime = obj.getString("startTime"),
            endTime = obj.getString("endTime"),
            venue = obj.getString("venue")
        )
    }
}

data class UnitNameData(val code: String, val name: String)

fun parseUnitNamesJson(raw: String): List<UnitNameData> {
    val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val arr = JSONArray(cleaned)
    return (0 until arr.length()).map { i ->
        val obj = arr.getJSONObject(i)
        UnitNameData(code = obj.getString("code"), name = obj.getString("name"))
    }
}

fun parseUnitTopicsJson(raw: String): List<String> {
    val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val obj = org.json.JSONObject(cleaned)
    val arr = obj.getJSONArray("topics")
    return (0 until arr.length()).map { arr.getString(it) }
}