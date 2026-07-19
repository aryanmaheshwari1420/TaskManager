package com.aryanmaheshwari.taskmanager.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.data.local.TaskDatabase
import com.aryanmaheshwari.taskmanager.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * TaskViewModel handles business logic for tasks and ad-related events.
 */
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository
    val allTasks: LiveData<List<Task>>

    // SharedPreferences to track ad count and premium status
    private val sharedPref = application.getSharedPreferences("ads_pref", Context.MODE_PRIVATE)
    
    private var taskCountInternal: Int
        get() = sharedPref.getInt("task_count", 0)
        set(value) = sharedPref.edit().putInt("task_count", value).apply()

    // Premium status (watch rewarded ad to unlock for the day)
    val isPremium: Boolean
        get() {
            val lastDate = sharedPref.getString("premium_date", "")
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            return lastDate == currentDate
        }

    // Free task restriction limit (set to 10 for demonstration)
    private val FREE_TASK_LIMIT = 10

    init {
        val database = TaskDatabase.getDatabase(application)
        repository = TaskRepository(database.taskDao(), database.checklistItemDao())
        allTasks = repository.allTasks
    }

    /**
     * Unlocks unlimited task creation for the current day.
     */
    fun setPremiumForToday() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        sharedPref.edit().putString("premium_date", currentDate).apply()
    }

    /**
     * Checks if task creation is restricted.
     */
    fun canAddTask(): Boolean {
        if (isPremium) return true
        val currentTaskCount = allTasks.value?.size ?: 0
        return currentTaskCount < FREE_TASK_LIMIT
    }

    /**
     * Logic to check if an interstitial ad should be shown.
     * Resets the counter if true.
     */
    fun checkAndResetInterstitialTrigger(): Boolean {
        if (taskCountInternal >= 4) {
            taskCountInternal = 0
            return true
        }
        return false
    }

    fun search(query: String): LiveData<List<Task>> = repository.searchTasks("%$query%")

    fun delete(task: Task) = viewModelScope.launch(Dispatchers.IO) { repository.delete(task) }

    fun update(task: Task) = viewModelScope.launch(Dispatchers.IO) { repository.update(task) }

    fun insert(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(task)
        
        // Increment counter after successful insertion
        taskCountInternal++
    }
}
