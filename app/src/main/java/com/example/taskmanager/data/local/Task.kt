package com.example.taskmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

// Room entity mapped to "task_table"
@Entity(tableName = "task_table")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,       // Required
    val description: String, // Optional
) : Serializable             // Needed to pass via Intent