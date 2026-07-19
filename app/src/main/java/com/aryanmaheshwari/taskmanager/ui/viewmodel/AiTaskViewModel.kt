package com.aryanmaheshwari.taskmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aryanmaheshwari.taskmanager.data.local.GeneratedTask
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.data.local.TaskDatabase
import com.aryanmaheshwari.taskmanager.data.repository.AiTaskRepository
import com.aryanmaheshwari.taskmanager.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel managing all state for the AI Task Generator BottomSheet.
 * No business logic lives in the UI layer.
 */
class AiTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepository = AiTaskRepository()
    private val taskRepository: TaskRepository

    private val _aiState = MutableLiveData<AiState>(AiState.Idle)
    val aiState: LiveData<AiState> = _aiState

    init {
        val database = TaskDatabase.getDatabase(application)
        taskRepository = TaskRepository(database.taskDao(), database.checklistItemDao())
    }

    // -------------------------------------------------------------------------
    // Generation
    // -------------------------------------------------------------------------

    /**
     * Kicks off Gemini API call. Posts [AiState.Loading] immediately then
     * resolves to [AiState.Success] or [AiState.Error].
     */
    fun generateTask(prompt: String) {
        if (prompt.isBlank()) {
            _aiState.value = AiState.Error("Please describe your goal first.")
            return
        }
        _aiState.value = AiState.Loading
        viewModelScope.launch {
            val result = aiRepository.generateTask(prompt)
            result
                .onSuccess { task -> _aiState.value = AiState.Success(task) }
                .onFailure { err -> _aiState.value = AiState.Error(err.message ?: "Failed to generate task.") }
        }
    }

    /** Resets state to Idle (e.g., when sheet is dismissed). */
    fun resetState() {
        _aiState.value = AiState.Idle
    }

    // -------------------------------------------------------------------------
    // Saving
    // -------------------------------------------------------------------------

    /**
     * Formats [GeneratedTask] metadata into the description field and persists
     * the task to the Room database. Does NOT save automatically — called only
     * when the user explicitly clicks Save.
     */
    fun saveGeneratedTask(generated: GeneratedTask, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Handle duplicate titles gracefully
                if (taskRepository.isDuplicateTitle(generated.title)) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "A task with this title already exists.")
                    }
                    return@launch
                }

                val formattedDesc = buildFormattedDescription(generated)
                val task = Task(id = 0, title = generated.title, description = formattedDesc)
                
                // Extract checklist item texts from the GeneratedTask preview
                val checklistTexts = generated.checklist.map { it.text }
                
                // Save task and checklist items using existing repository logic
                taskRepository.insertTaskWithChecklist(task, checklistTexts)

                withContext(Dispatchers.Main) { 
                    onComplete(true, null) 
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "Failed to save task")
                }
            }
        }
    }

    /**
     * Builds a human-readable description string that embeds priority, category,
     * due date, and checklist items so they survive in the existing Task schema.
     */
    fun buildFormattedDescription(generated: GeneratedTask): String {
        return buildString {
            append(generated.description)
            if (generated.description.isNotBlank()) append("\n\n")
            if (generated.priority.isNotBlank()) append("Priority: ${generated.priority}\n")
            generated.category?.let { append("Category: ${it.name}\n") }
            if (generated.dueDate.isNotBlank())   append("Due: ${generated.dueDate}\n")
            // Note: Checklist items are also saved to their own table, 
            // but we append them here for compatibility with existing "Edit" flow.
            if (generated.checklist.isNotEmpty()) {
                append("\nChecklist:\n")
                generated.checklist.forEach { append("• ${it.text}\n") }
            }
        }.trim()
    }
}

// -----------------------------------------------------------------------------
// State
// -----------------------------------------------------------------------------

/**
 * Sealed interface representing all possible states of the AI generation flow.
 */
sealed interface AiState {
    /** Initial state — prompt form is visible. */
    object Idle : AiState
    /** Gemini API call in progress. */
    object Loading : AiState
    /** Generation succeeded — preview is ready. */
    data class Success(val task: GeneratedTask) : AiState
    /** Generation failed — message contains a user-friendly description. */
    data class Error(val message: String) : AiState
}
