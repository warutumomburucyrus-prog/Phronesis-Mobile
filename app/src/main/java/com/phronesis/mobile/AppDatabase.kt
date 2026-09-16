package com.phronesis.mobile

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Assignment::class, NoteEntity::class, ClassSessionEntity::class, UnitEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assignmentDao(): AssignmentDao
    abstract fun noteDao(): NoteDao
    abstract fun classSessionDao(): ClassSessionDao
    abstract fun unitDao(): UnitDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phronesis-database"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}