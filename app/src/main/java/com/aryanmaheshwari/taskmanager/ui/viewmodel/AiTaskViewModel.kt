package com.aryanmaheshwari.taskmanager.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aryanmaheshwari.taskmanager.data.local.GeneratedTask
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.data.local.TaskDatabase
import com.aryanmaheshwari.taskmanager.data.repository.AiTaskRepository
import com.aryanmaheshwari.taskmanager.data.repository.TaskRepository
import com.aryanmaheshwari.taskmanager.utils.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel managing all state for the AI Task Generator BottomSheet.
 * Handles both text and audio input for task generation.
 * No business logic lives in the UI layer.
 */
class AiTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepository = AiTaskRepository()
    private val taskRepository: TaskRepository
    private val audioRecorder = AudioRecorder(application.cacheDir)

    private val _aiState = MutableLiveData<AiState>(AiState.Idle)
    val aiState: LiveData<AiState> = _aiState

    private val _recordingState = MutableLiveData<RecordingState>(RecordingState.Idle)
    val recordingState: LiveData<RecordingState> = _recordingState

    companion object {
        private const val TAG = "AiTaskViewModel"
    }

    init {
        val database = TaskDatabase.getDatabase(application)
        taskRepository = TaskRepository(database.taskDao(), database.checklistItemDao())
    }

    // -------------------------------------------------------------------------
    // Text-based Generation
    // -------------------------------------------------------------------------

    /**
     * Kicks off Gemini API call for text input. Posts [AiState.Loading] immediately then
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
                .onFailure { err -> 
                    Log.e(TAG, "Text generation failed", err)
                    _aiState.value = AiState.Error(err.message ?: "Failed to generate task.") 
                }
        }
    }

    // -------------------------------------------------------------------------
    // Audio-based Generation
    // -------------------------------------------------------------------------

    /**
     * Starts recording audio from the microphone.
     * Updates [RecordingState] to [RecordingState.Recording].
     */
    fun startRecording(): Boolean {
        Log.d(TAG, "Attempting to start recording...")
        return if (audioRecorder.startRecording()) {
            _recordingState.value = RecordingState.Recording
            true
        } else {
            Log.e(TAG, "AudioRecorder failed to start")
            _recordingState.value = RecordingState.Error("Failed to start recording. Check microphone permissions.")
            false
        }
    }

    /**
     * Stops recording and initiates Gemini processing.
     * Updates [AiState] to [AiState.Loading] and sends audio to Gemini.
     */
    fun stopRecordingAndProcess() {
        Log.d(TAG, "stopRecordingAndProcess() called")
        _recordingState.value = RecordingState.Processing

        val audioFile = audioRecorder.stopRecording()
        if (audioFile == null) {
            Log.e(TAG, "stopRecording() returned null - file creation failed")
            _recordingState.value = RecordingState.Error("Recording failed. Please try again.")
            return
        }

        Log.d(TAG, "Audio recorded: ${audioFile.absolutePath}, Size: ${audioFile.length()} bytes")

        if (audioFile.length() <= 0) {
            Log.e(TAG, "Audio file is empty!")
            _recordingState.value = RecordingState.Error("Audio file is empty. Please speak louder or check microphone.")
            return
        }

        _aiState.value = AiState.Loading
        viewModelScope.launch {
            Log.d(TAG, "Sending audio to Gemini repository...")
            val result = aiRepository.generateTaskFromAudio(audioFile)
            result
                .onSuccess { task ->
                    Log.d(TAG, "Gemini Success: Generated task '${task.title}'")
                    _recordingState.value = RecordingState.Idle
                    _aiState.value = AiState.Success(task)
                }
                .onFailure { err ->
                    Log.e(TAG, "Gemini audio processing failed!", err)
                    _recordingState.value = RecordingState.Error(err.message ?: "Failed to process audio.")
                    _aiState.value = AiState.Error(err.message ?: "Failed to generate task from audio.")
                }
        }
    }

    /**
     * Cancels the current recording.
     */
    fun cancelRecording() {
        Log.d(TAG, "Recording cancelled by user")
        audioRecorder.cancelRecording()
        _recordingState.value = RecordingState.Idle
    }

    // -------------------------------------------------------------------------
    // Common Operations
    // -------------------------------------------------------------------------

    /** Resets state to Idle (e.g., when sheet is dismissed). */
    fun resetState() {
        _aiState.value = AiState.Idle
        _recordingState.value = RecordingState.Idle
        audioRecorder.cancelRecording()
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
                Log.e(TAG, "Error saving generated task", e)
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

    override fun onCleared() {
        super.onCleared()
        // Ensure recording is cancelled and cleaned up
        audioRecorder.cancelRecording()
    }
}

// =========================================================================
// State Models
// =========================================================================

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

/**
 * Represents states of the audio recording process.
 */
sealed interface RecordingState {
    /** Initial state — not recording. */
    object Idle : RecordingState
    /** Actively recording audio from microphone. */
    object Recording : RecordingState
    /** Recording stopped, audio is being processed by Gemini. */
    object Processing : RecordingState
    /** Recording or processing failed. */
    data class Error(val message: String) : RecordingState
}