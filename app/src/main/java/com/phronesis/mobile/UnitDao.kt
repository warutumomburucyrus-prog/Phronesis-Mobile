package com.phronesis.mobile

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY code")
    fun getAll(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): UnitEntity?

    @Insert
    suspend fun insert(unit: UnitEntity)

    @Update
    suspend fun update(unit: UnitEntity)

    @Delete
    suspend fun delete(unit: UnitEntity)
}