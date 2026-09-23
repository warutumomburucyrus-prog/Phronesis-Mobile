package com.phronesis.mobile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "topic_progress")
data class TopicProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitCode: String,
    val topic: String,
    val familiarity: Int = 0,   // 0-100, rises when the topic is studied (notes summarized, marked reviewed)
    val mastery: Int = 0,       // 0-100, derived from quiz accuracy on this topic
    val quizzesTaken: Int = 0,  // how many quizzes have covered this topic — used to average mastery over time
)