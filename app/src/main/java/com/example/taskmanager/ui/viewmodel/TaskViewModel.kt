package com.example.taskmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.taskmanager.data.local.Task
import com.example.taskmanager.data.local.TaskDatabase
import com.example.taskmanager.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Survives config changes (e.g. rotation), exposes data via LiveData
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository
    val allTasks: LiveData<List<Task>>

    init {
        val dao = TaskDatabase.getDatabase(application).taskDao()
        repository = TaskRepository(dao)
        allTasks = repository.allTasks
    }

    // Wraps query with % for partial matching
    fun search(query: String): LiveData<List<Task>> = repository.searchTasks("%$query%")

    fun delete(task: Task) = viewModelScope.launch(Dispatchers.IO) { repository.delete(task) }

    fun update(task: Task) = viewModelScope.launch(Dispatchers.IO) { repository.update(task) }

    fun insert(task: Task) = viewModelScope.launch(Dispatchers.IO) { repository.insert(task) }
}