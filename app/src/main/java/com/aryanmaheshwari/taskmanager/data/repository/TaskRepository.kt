package com.aryanmaheshwari.taskmanager.data.repository

import androidx.lifecycle.LiveData
import com.aryanmaheshwari.taskmanager.data.local.ChecklistItem
import com.aryanmaheshwari.taskmanager.data.local.ChecklistItemDao
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.data.local.TaskDao

// Single source of truth — ViewModel talks to this, not the DAOs directly
class TaskRepository(
    private val taskDao: TaskDao,
    private val checklistItemDao: ChecklistItemDao
) {

    val allTasks: LiveData<List<Task>> = taskDao.getAllTasks()

    suspend fun insert(task: Task) = taskDao.insertTask(task)

    suspend fun update(task: Task) = taskDao.updateTask(task)

    suspend fun delete(task: Task) = taskDao.deleteTask(task)

    fun searchTasks(query: String): LiveData<List<Task>> = taskDao.searchTasks(query)

    // --- AI-generated task support (reuses existing DAO calls only) ---

    suspend fun isDuplicateTitle(title: String): Boolean = taskDao.countByTitle(title) > 0

    /**
     * Inserts a task and its checklist items together, using the existing
     * insertTaskReturningId() / insertChecklistItems() DAO methods.
     * Returns the new task id.
     */
    suspend fun insertTaskWithChecklist(task: Task, checklistTexts: List<String>): Long {
        val newTaskId = taskDao.insertTaskReturningId(task)
        if (checklistTexts.isNotEmpty()) {
            val items = checklistTexts
                .filter { it.isNotBlank() }
                .map { ChecklistItem(taskId = newTaskId.toInt(), text = it.trim()) }
            if (items.isNotEmpty()) {
                checklistItemDao.insertChecklistItems(items)
            }
        }
        return newTaskId
    }

    fun getChecklistItems(taskId: Int): LiveData<List<ChecklistItem>> =
        checklistItemDao.getChecklistItemsForTask(taskId)
}
