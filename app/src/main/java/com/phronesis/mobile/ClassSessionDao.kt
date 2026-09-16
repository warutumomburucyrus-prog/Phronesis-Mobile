package com.phronesis.mobile

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassSessionDao {
    @Query("SELECT * FROM class_sessions ORDER BY day, startTime")
    fun getAll(): Flow<List<ClassSessionEntity>>

    @Insert
    suspend fun insert(session: ClassSessionEntity)

    @Update
    suspend fun update(session: ClassSessionEntity)

    @Delete
    suspend fun delete(session: ClassSessionEntity)

    @Query("DELETE FROM class_sessions")
    suspend fun clearAll()
}