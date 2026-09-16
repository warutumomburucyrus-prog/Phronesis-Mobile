package com.phronesis.mobile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_sessions")
data class ClassSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val unitCode: String,
    val startTime: String,
    val endTime: String,
    val venue: String
)