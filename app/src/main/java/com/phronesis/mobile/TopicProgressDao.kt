package com.phronesis.mobile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicProgressDao {
    @Query("SELECT * FROM topic_progress WHERE unitCode = :unitCode")
    fun getForUnit(unitCode: String): Flow<List<TopicProgressEntity>>

    @Query("SELECT * FROM topic_progress")
    fun getAll(): Flow<List<TopicProgressEntity>>

    @Query("SELECT * FROM topic_progress WHERE unitCode = :unitCode AND topic = :topic LIMIT 1")
    suspend fun getOne(unitCode: String, topic: String): TopicProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TopicProgressEntity)

    @Update
    suspend fun update(entry: TopicProgressEntity)
}