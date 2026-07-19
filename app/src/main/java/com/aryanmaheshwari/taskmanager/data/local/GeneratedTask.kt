package com.aryanmaheshwari.taskmanager.data.local

/**
 * Represents the fully structured task returned from Gemini API.
 * This is a preview model — kept in-memory before the user decides to save.
 */
data class GeneratedTask(
    val title: String,
    val description: String,
    /** HIGH / MEDIUM / LOW */
    val priority: String,
    val category: CategoryEntity?,
    /** Relative due date, e.g. "In 3 days", "Next Monday" */
    val dueDate: String,
    val checklist: List<ChecklistItem>
)
