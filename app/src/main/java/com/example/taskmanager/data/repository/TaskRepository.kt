package com.example.taskmanager.data.repository

import androidx.lifecycle.LiveData
import com.example.taskmanager.data.local.Task
import com.example.taskmanager.data.local.TaskDao

// Single source of truth — ViewModel talks to this, not the DAO directly
class TaskRepository(private val taskDao: TaskDao) {

    val allTasks: LiveData<List<Task>> = taskDao.getAllTasks()

    suspend fun insert(task: Task) = taskDao.insertTask(task)

    suspend fun update(task: Task) = taskDao.updateTask(task)

    suspend fun delete(task: Task) = taskDao.deleteTask(task)

    fun searchTasks(query: String): LiveData<List<Task>> = taskDao.searchTasks(query)
}
