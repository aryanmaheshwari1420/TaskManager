package com.aryanmaheshwari.taskmanager.ui.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aryanmaheshwari.taskmanager.data.local.GeneratedTask
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.data.local.TaskDatabase
import com.aryanmaheshwari.taskmanager.data.local.AiSnapshot
import com.aryanmaheshwari.taskmanager.data.local.AiSnapshotDao
import com.aryanmaheshwari.taskmanager.data.local.CategoryEntity
import com.aryanmaheshwari.taskmanager.data.local.ChecklistItem
import com.aryanmaheshwari.taskmanager.data.local.toAiSnapshot
import com.aryanmaheshwari.taskmanager.data.local.toGeneratedTask
import com.aryanmaheshwari.taskmanager.data.repository.AiTaskRepository
import com.aryanmaheshwari.taskmanager.data.repository.TaskRepository
import com.aryanmaheshwari.taskmanager.utils.AudioRecorder
import com.aryanmaheshwari.taskmanager.utils.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel managing all state for the AI Task Generator BottomSheet.
 * Handles both text and audio input for task generation in an offline-first manner.
 */
class AiTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepository = AiTaskRepository()
    private val taskRepository: TaskRepository
    private val aiSnapshotDao: AiSnapshotDao
    private val audioRecorder = AudioRecorder(application.cacheDir)

    private val networkMonitor = NetworkMonitor(application)
    val isOnline: LiveData<Boolean> = networkMonitor.isOnline

    private val _aiState = MutableLiveData<AiState>(AiState.Idle)
    val aiState: LiveData<AiState> = _aiState

    private val _recordingState = MutableLiveData<RecordingState>(RecordingState.Idle)
    val recordingState: LiveData<RecordingState> = _recordingState

    val allAiSnapshots: LiveData<List<AiSnapshot>>

    companion object {
        private const val TAG = "AiTaskViewModel"
    }

    init {
        val database = TaskDatabase.getDatabase(application)
        taskRepository = TaskRepository(database.taskDao(), database.checklistItemDao())
        aiSnapshotDao = database.aiSnapshotDao()
        allAiSnapshots = aiSnapshotDao.getAllSnapshots()
    }

    // -------------------------------------------------------------------------
    // Text-based Generation
    // -------------------------------------------------------------------------

    fun generateTask(prompt: String) {
        if (prompt.isBlank()) {
            _aiState.value = AiState.Error("Please describe your goal first.")
            return
        }

        // Offline-First Check: If offline, save prompt as a local pending draft
        if (networkMonitor.isCurrentlyOnline() == false) {
            saveOfflineDraft(prompt, isVoice = false, audioPath = null)
            return
        }

        _aiState.value = AiState.Loading
        viewModelScope.launch {
            val result = aiRepository.generateTask(prompt)
            result
                .onSuccess { task ->
                    val taskWithTranscript = if (task.transcript.isNullOrBlank()) {
                        task.copy(transcript = prompt)
                    } else {
                        task
                    }
                    // Cache successful generation locally
                    insertAiSnapshot(taskWithTranscript, prompt, isVoice = false, audioPath = null)
                    _aiState.value = AiState.Success(taskWithTranscript)
                }
                .onFailure { err ->
                    Log.e(TAG, "Text generation failed", err)
                    _aiState.value = AiState.Error(err.message ?: "Failed to generate task.")
                }
        }
    }

    // -------------------------------------------------------------------------
    // Audio-based Generation
    // -------------------------------------------------------------------------

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

    fun stopRecordingAndProcess() {
        Log.d(TAG, "stopRecordingAndProcess() called")
        _recordingState.value = RecordingState.Processing

        val audioFile = audioRecorder.stopRecording()
        if (audioFile == null) {
            Log.e(TAG, "stopRecording() returned null")
            _recordingState.value = RecordingState.Error("Recording failed. Please try again.")
            return
        }

        // Offline-First Check: If offline, save recorded audio as a local pending voice draft
        if (networkMonitor.isCurrentlyOnline() == false) {
            saveOfflineDraft(prompt = "[Spoken request]", isVoice = true, audioPath = audioFile.absolutePath)
            return
        }

        _aiState.value = AiState.Loading
        viewModelScope.launch {
            val result = aiRepository.generateTaskFromAudio(audioFile)
            result
                .onSuccess { task ->
                    _recordingState.value = RecordingState.Idle
                    // Cache successful voice generation locally
                    insertAiSnapshot(task, task.transcript ?: "[Spoken request]", isVoice = true, audioPath = null)
                    _aiState.value = AiState.Success(task)
                }
                .onFailure { err ->
                    _recordingState.value = RecordingState.Error(err.message ?: "Failed to process audio.")
                    _aiState.value = AiState.Error(err.message ?: "Failed to generate task from audio.")
                }
        }
    }

    fun cancelRecording() {
        Log.d(TAG, "Recording cancelled by user")
        audioRecorder.cancelRecording()
        _recordingState.value = RecordingState.Idle
    }

    // -------------------------------------------------------------------------
    // Offline Cache & Snapshot Management
    // -------------------------------------------------------------------------

    fun saveOfflineDraft(prompt: String, isVoice: Boolean, audioPath: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val placeholderTask = GeneratedTask(
                title = if (isVoice) "Voice AI Request" else prompt.take(30).trim() + "...",
                description = "Offline draft saved. Tap below to generate when back online.",
                priority = "MEDIUM",
                category = null,
                dueDate = "",
                checklist = emptyList(),
                transcript = if (isVoice) "[Audio recording queued]" else prompt
            )
            val snapshot = placeholderTask.toAiSnapshot(prompt, isVoice, audioPath, isPending = true)
            aiSnapshotDao.insert(snapshot)
            withContext(Dispatchers.Main) {
                _aiState.value = AiState.Idle
                _recordingState.value = RecordingState.Idle
                Toast.makeText(getApplication(), "Offline: AI draft saved. Sync when online.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun insertAiSnapshot(task: GeneratedTask, prompt: String, isVoice: Boolean, audioPath: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = task.toAiSnapshot(prompt, isVoice, audioPath, isPending = false)
            aiSnapshotDao.insert(snapshot)
        }
    }

    fun deleteSnapshot(snapshot: AiSnapshot) {
        viewModelScope.launch(Dispatchers.IO) {
            aiSnapshotDao.delete(snapshot)
            if (snapshot.isVoice && snapshot.audioFilePath != null) {
                try {
                    File(snapshot.audioFilePath).delete()
                } catch (e: Exception) {
                    // Ignore deletion cleanup errors
                }
            }
        }
    }

    fun loadSnapshotPreview(snapshot: AiSnapshot) {
        _aiState.value = AiState.Success(snapshot.toGeneratedTask())
    }

    fun retryPendingSnapshot(snapshot: AiSnapshot, onConnectionError: () -> Unit) {
        if (networkMonitor.isCurrentlyOnline() == false) {
            onConnectionError()
            return
        }

        _aiState.value = AiState.Loading
        viewModelScope.launch {
            // Delete the pending snapshot once retrying starts to prevent duplicates
            aiSnapshotDao.deleteById(snapshot.id)

            if (snapshot.isVoice && snapshot.audioFilePath != null) {
                val audioFile = File(snapshot.audioFilePath)
                if (audioFile.exists()) {
                    val result = aiRepository.generateTaskFromAudio(audioFile)
                    result
                        .onSuccess { task ->
                            insertAiSnapshot(task, task.transcript ?: snapshot.prompt, isVoice = true, audioPath = null)
                            _aiState.value = AiState.Success(task)
                        }
                        .onFailure { err ->
                            // Restore draft on failure
                            saveOfflineDraft(snapshot.prompt, isVoice = true, audioPath = snapshot.audioFilePath)
                            _aiState.value = AiState.Error(err.message ?: "Failed to process audio.")
                        }
                } else {
                    _aiState.value = AiState.Error("Audio file no longer exists.")
                }
            } else {
                val result = aiRepository.generateTask(snapshot.prompt)
                result
                    .onSuccess { task ->
                        val taskWithTranscript = if (task.transcript.isNullOrBlank()) {
                            task.copy(transcript = snapshot.prompt)
                        } else {
                            task
                        }
                        insertAiSnapshot(taskWithTranscript, snapshot.prompt, isVoice = false, audioPath = null)
                        _aiState.value = AiState.Success(taskWithTranscript)
                    }
                    .onFailure { err ->
                        // Restore draft on failure
                        saveOfflineDraft(snapshot.prompt, isVoice = false, audioPath = null)
                        _aiState.value = AiState.Error(err.message ?: "Failed to generate task.")
                    }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Common Operations
    // -------------------------------------------------------------------------

    fun resetState() {
        _aiState.value = AiState.Idle
        _recordingState.value = RecordingState.Idle
        audioRecorder.cancelRecording()
    }

    fun saveGeneratedTask(generated: GeneratedTask, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (taskRepository.isDuplicateTitle(generated.title)) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "A task with this title already exists.")
                    }
                    return@launch
                }

                val formattedDesc = buildFormattedDescription(generated)
                val task = Task(id = 0, title = generated.title, description = formattedDesc)
                val checklistTexts = generated.checklist.map { it.text }

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

    fun buildFormattedDescription(generated: GeneratedTask): String {
        return buildString {
            append(generated.description)
            if (generated.description.isNotBlank()) append("\n\n")
            if (generated.priority.isNotBlank()) append("Priority: ${generated.priority}\n")
            generated.category?.let { append("Category: ${it.name}\n") }
            if (generated.dueDate.isNotBlank())   append("Due: ${generated.dueDate}\n")
            if (generated.checklist.isNotEmpty()) {
                append("\nChecklist:\n")
                generated.checklist.forEach { append("• ${it.text}\n") }
            }
        }.trim()
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.cancelRecording()
        networkMonitor.unregister()
    }
}

// =========================================================================
// State Models
// =========================================================================

/**
 * Sealed interface representing all possible states of the AI generation flow.
 */
sealed interface AiState {
    object Idle : AiState
    object Loading : AiState
    data class Success(val task: GeneratedTask) : AiState
    data class Error(val message: String) : AiState
}

/**
 * Represents states of the audio recording process.
 */
sealed interface RecordingState {
    object Idle : RecordingState
    object Recording : RecordingState
    object Processing : RecordingState
    data class Error(val message: String) : RecordingState
}