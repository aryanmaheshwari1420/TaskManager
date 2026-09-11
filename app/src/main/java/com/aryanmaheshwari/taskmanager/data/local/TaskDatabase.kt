package com.aryanmaheshwari.taskmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class, ChecklistItem::class, AiSnapshot::class], version = 2, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun checklistItemDao(): ChecklistItemDao
    abstract fun aiSnapshotDao(): AiSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        // Room Migration from 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_snapshot_table` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `prompt` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `priority` TEXT NOT NULL, 
                        `categoryName` TEXT, 
                        `dueDate` TEXT NOT NULL, 
                        `checklistJson` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `isVoice` INTEGER NOT NULL, 
                        `audioFilePath` TEXT, 
                        `isPending` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // Singleton — only one DB instance across the app
        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
