package com.aryanmaheshwari.taskmanager.data.repository

import com.aryanmaheshwari.taskmanager.data.local.GeneratedTask
import com.aryanmaheshwari.taskmanager.data.remote.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Repository that exposes AI-powered task generation to the ViewModel layer.
 * All network calls are executed on [Dispatchers.IO].
 */
class AiTaskRepository(private val geminiService: GeminiService = GeminiService()) {

    /**
     * Generates a [GeneratedTask] from the given user prompt.
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
            } catch (e: IllegalArgumentException) {
                // API key not configured
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "An unexpected error occurred. Please try again."))
            }
        }
}
