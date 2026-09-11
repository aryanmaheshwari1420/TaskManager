package com.aryanmaheshwari.taskmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "ai_snapshot_table")
data class AiSnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val prompt: String,
    val title: String,
    val description: String,
    val priority: String,
    val categoryName: String?,
    val dueDate: String,
    val checklistJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isVoice: Boolean,
    val audioFilePath: String?,
    val isPending: Boolean
) : Serializable

fun GeneratedTask.toAiSnapshot(prompt: String, isVoice: Boolean, audioFilePath: String?, isPending: Boolean): AiSnapshot {
    val checklistJson = checklist.map { it.text }.joinToString("||")
    return AiSnapshot(
        prompt = prompt,
        title = title,
        description = description,
        priority = priority,
        categoryName = category?.name,
        dueDate = dueDate,
        checklistJson = checklistJson,
        isVoice = isVoice,
        audioFilePath = audioFilePath,
        isPending = isPending
    )
}

fun AiSnapshot.toGeneratedTask(): GeneratedTask {
    val items = if (checklistJson.isBlank()) {
        emptyList()
    } else {
        checklistJson.split("||").map { ChecklistItem(taskId = 0, text = it) }
    }
    return GeneratedTask(
        title = title,
        description = description,
        priority = priority,
        category = categoryName?.let { CategoryEntity(it) },
        dueDate = dueDate,
        checklist = items,
        transcript = if (isVoice) prompt else null
    )
}
