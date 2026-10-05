package com.phronesis.mobile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import androidx.room.Delete

@Dao
interface AssignmentDao {

    @Query("SELECT * FROM assignments ORDER BY deadline ASC")
    fun getAll(): Flow<List<Assignment>>

    @Insert
    suspend fun insert(assignment: Assignment)

    @Update
    suspend fun update(assignment: Assignment)

    @Delete
    suspend fun delete(assignment: Assignment)
}