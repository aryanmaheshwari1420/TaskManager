package com.aryanmaheshwari.taskmanager.data.repository

import com.aryanmaheshwari.taskmanager.data.local.GeneratedTask
import com.aryanmaheshwari.taskmanager.data.remote.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Repository that exposes AI-powered task generation to the ViewModel layer.
 * Supports both text and audio input.
 * All network calls are executed on [Dispatchers.IO].
 */
class AiTaskRepository(private val geminiService: GeminiService = GeminiService()) {

    /**
     * Generates a [GeneratedTask] from the given user text prompt.
     *
     * Returns:
     * - [Result.success] with the [GeneratedTask] on success.
     * - [Result.failure] with a user-friendly exception on any error.
     */
    suspend fun generateTask(prompt: String): Result<GeneratedTask> =
        withContext(Dispatchers.IO) {
            try {
                val task = geminiService.generateTask(prompt)
                Result.success(task)
            } catch (e: SocketTimeoutException) {
                Result.failure(IOException("Request timed out. Please check your connection and try again."))
            } catch (e: UnknownHostException) {
                Result.failure(IOException("No internet connection. Please go online and try again."))
            } catch (e: retrofit2.HttpException) {
                val userFriendlyMsg = when (e.code()) {
                    503 -> "Gemini AI is experiencing high demand right now. Please try again in a few seconds."
                    429 -> "Too many requests. Please wait a moment before trying again."
                    403 -> "Authentication failed. Please verify your API key."
                    400 -> "Bad request. The prompt might be invalid or flagged."
                    else -> "AI service temporarily unavailable (HTTP ${e.code()}). Please try again."
                }
                Result.failure(IOException(userFriendlyMsg, e))
            } catch (e: IllegalArgumentException) {
                // API key not configured
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "An unexpected error occurred. Please try again."))
            }
        }

    /**
     * Generates a [GeneratedTask] from an audio file via speech-to-task processing.
     *
     * The audio file is sent to Gemini, which understands the speech and extracts
     * structured task information.
     *
     * Returns:
     * - [Result.success] with the [GeneratedTask] on success.
     * - [Result.failure] with a user-friendly exception on any error.
     */
    suspend fun generateTaskFromAudio(audioFile: File): Result<GeneratedTask> =
        withContext(Dispatchers.IO) {
            try {
                if (!audioFile.exists()) {
                    return@withContext Result.failure(
                        IOException("Audio file not found or recording failed.")
                    )
                }

                if (audioFile.length() == 0L) {
                    audioFile.delete()
                    return@withContext Result.failure(
                        IOException("No audio recorded. Please try again.")
                    )
                }

                val task = geminiService.generateTaskFromAudio(audioFile)
                Result.success(task)
            } catch (e: SocketTimeoutException) {
                Result.failure(IOException("Request timed out. Please check your connection and try again."))
            } catch (e: UnknownHostException) {
                Result.failure(IOException("No internet connection. Please go online and try again."))
            } catch (e: retrofit2.HttpException) {
                val userFriendlyMsg = when (e.code()) {
                    503 -> "Gemini AI is experiencing high demand right now. Please try again in a few seconds."
                    429 -> "Too many requests. Please wait a moment before trying again."
                    403 -> "Authentication failed. Please verify your API key."
                    400 -> "Bad request. The prompt might be invalid or flagged."
                    else -> "AI service temporarily unavailable (HTTP ${e.code()}). Please try again."
                }
                Result.failure(IOException(userFriendlyMsg, e))
            } catch (e: IllegalArgumentException) {
                // API key not configured or file not found
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "An unexpected error occurred. Please try again."))
            } finally {
                // Clean up the audio file after processing
                try {
                    audioFile.delete()
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
}