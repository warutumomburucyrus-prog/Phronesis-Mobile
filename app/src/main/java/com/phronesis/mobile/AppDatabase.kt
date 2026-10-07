package com.phronesis.mobile

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Assignment::class, NoteEntity::class, ClassSessionEntity::class, UnitEntity::class, TopicProgressEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assignmentDao(): AssignmentDao
    abstract fun noteDao(): NoteDao
    abstract fun classSessionDao(): ClassSessionDao
    abstract fun unitDao(): UnitDao
    abstract fun topicProgressDao(): TopicProgressDao

    companion object {

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE class_sessions ADD COLUMN courseName TEXT NOT NULL DEFAULT ''")
            }
        }
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phronesis-database"
                ).addMigrations(MIGRATION_5_6).build().also { INSTANCE = it }
            }
        }
    }
}