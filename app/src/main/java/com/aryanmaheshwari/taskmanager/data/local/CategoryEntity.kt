package com.aryanmaheshwari.taskmanager.data.local

/**
 * Represents a task category suggestion returned by Gemini.
 * Kept in-memory only — does not map to a Room table.
 */
data class CategoryEntity(val name: String)
