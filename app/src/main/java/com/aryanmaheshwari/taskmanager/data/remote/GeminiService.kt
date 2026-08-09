package com.aryanmaheshwari.taskmanager.data.remote

import android.util.Log
import com.aryanmaheshwari.taskmanager.BuildConfig
import com.aryanmaheshwari.taskmanager.data.local.CategoryEntity
import com.aryanmaheshwari.taskmanager.data.local.ChecklistItem
import com.aryanmaheshwari.taskmanager.data.local.GeneratedTask
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.getValue

/**
 * Service layer that communicates with Google's Gemini REST API via Retrofit + OkHttp.
 * Supports both text and audio input.
 * Returns strongly-typed Kotlin models — no raw JSON leaks to upper layers.
 */
class GeminiService(private val apiKey: String = BuildConfig.GEMINI_API_KEY) {

    companion object {
        private const val TAG = "GeminiService"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"
        private const val CONNECT_TIMEOUT_SECS = 30L
        private const val READ_TIMEOUT_SECS = 60L
        private const val WRITE_TIMEOUT_SECS = 30L
    }

    // ---------- Prompts -------------------------------------------------------

    private val systemInstructionText = """
        The AI should always return ONLY valid JSON.

        Expected format:
        {
          "title": "",
          "description": "",
          "priority": "",
          "category": "",
          "dueDate": "",
          "checklist": []
        }

        Rules:
        - Generate a meaningful title.
        - Generate 5-10 actionable checklist items.
        - Infer category.
        - Infer priority.
        - Infer due date if user mentions time.
        - Never return markdown.
        - Never explain.
        - Never wrap JSON inside code blocks.
        - Never return extra text.
        - If information is missing, make intelligent assumptions.
        - Optimize prompt for consistent JSON generation.
        
        CRITICAL: Respond ONLY with the raw JSON object. Do not include introductory text, explanations, or markdown code blocks like ```json.
    """.trimIndent()

    private fun buildAudioSystemPrompt(): String {
        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val tzFormat = SimpleDateFormat("z", Locale.getDefault())

        val currentDate = dateFormat.format(now.time)
        val currentTime = timeFormat.format(now.time)
        val timezone = TimeZone.getDefault().id

        return """
        You are a task extraction assistant for spoken input (Hindi/English mixed speech).
        
        Convert the user's spoken request into structured task/checklist information.
        
        Current date: $currentDate
        Current time: $currentTime
        Timezone: $timezone
        
        Extract ONLY information actually present in the speech.
        Do NOT invent missing information.
        
        Expected format:
        {
          "title": "Task title (required)",
          "description": "Optional description",
          "priority": "HIGH/MEDIUM/LOW or null",
          "category": "Inferred category or null",
          "dueDate": "Relative or absolute date or null",
          "checklist": ["Item 1", "Item 2", ...] or []
        }
        
        Rules:
        1. Understand Hindi/English mixed speech.
        2. Understand relative dates: "kal" (tomorrow), "aaj" (today), "next Monday".
        3. Understand relative times: "shaam" (evening), "subah" (morning).
        4. If user mentions multiple items, add them as checklist items.
        5. Return ONLY valid JSON.
        6. Never return markdown or code blocks.
        7. Never explain.
        8. If title is missing, return null or empty string.
        
        CRITICAL: Respond ONLY with the raw JSON object.
    """.trimIndent()
    }

    // ---------- Retrofit / OkHttp --------------------------------------------

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECS, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    // ---------- Public API ---------------------------------------------------

    /**
     * Generates a fully structured [GeneratedTask] from a user text prompt.
     * Throws descriptive exceptions on network or parsing failures.
     */
    suspend fun generateTask(prompt: String): GeneratedTask {
        require(apiKey.isNotBlank()) {
            "Gemini API key is not configured. Add gemini.api.key to local.properties."
        }

        val fullPrompt = "$systemInstructionText\n\nUser goal: $prompt"

        val request = GeminiRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = fullPrompt)))
            ),
            generationConfig = GenerationConfig(responseMimeType = "application/json")
        )

        val response = apiService.generateContent(apiKey, request)

        val rawText = response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: throw IllegalStateException("Gemini returned an empty response. Please try again.")

        return parseGeneratedTask(rawText)
    }

    /**
     * Generates a [GeneratedTask] from an audio file via speech processing.
     *
     * @param audioFile The audio file to process (MP3, WAV, etc.)
     * @throws IllegalArgumentException if API key is not configured
     * @throws IllegalStateException if Gemini returns empty response
     * @throws JSONException if response JSON is malformed
     */
    suspend fun generateTaskFromAudio(audioFile: File): GeneratedTask {
        require(apiKey.isNotBlank()) {
            "Gemini API key is not configured. Add gemini.api.key to local.properties."
        }
        require(audioFile.exists()) {
            "Audio file does not exist: ${audioFile.absolutePath}"
        }

        // Determine MIME type based on file extension
        val mimeType = when (audioFile.extension.lowercase()) {
            "mp3" -> "audio/mp3"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            else -> "audio/mp3" // Default to MP3
        }

        // Convert audio file to base64
        val base64Audio = audioFile.readBytes().let {
            android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
        }

        val audioSystemPrompt = buildAudioSystemPrompt()

        val request = GeminiRequest(
            contents = listOf(
                Content(parts = listOf(
                    Part(text = audioSystemPrompt),
                    Part(inlineData = InlineData(
                        mimeType = mimeType,
                        data = base64Audio
                    ))
                ))
            ),
            generationConfig = GenerationConfig(responseMimeType = "application/json")
        )

        val response = apiService.generateContent(apiKey, request)

        val rawText = response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: throw IllegalStateException("Gemini returned an empty response. Please try again.")

        return parseGeneratedTask(rawText)
    }

    // ---------- Private helpers ----------------------------------------------

    private fun parseGeneratedTask(jsonText: String): GeneratedTask {
        try {
            val clean = stripMarkdownFences(jsonText)
            val obj = JSONObject(clean)

            val checklistArray = obj.optJSONArray("checklist")
            val checklist = buildList {
                if (checklistArray != null) {
                    for (i in 0 until checklistArray.length()) {
                        add(ChecklistItem(taskId = 0, text = checklistArray.getString(i)))
                    }
                }
            }

            val categoryName = obj.optString("category", "").trim()

            return GeneratedTask(
                title       = obj.optString("title", "Generated Task").trim(),
                description = obj.optString("description", "").trim(),
                priority    = obj.optString("priority", "MEDIUM").uppercase().trim(),
                category    = if (categoryName.isNotBlank()) CategoryEntity(categoryName) else null,
                dueDate     = obj.optString("dueDate", "").trim(),
                checklist   = checklist
            )
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parse failure: $jsonText", e)
            throw IllegalStateException("Could not understand the AI response. Please try again.")
        }
    }

    private fun stripMarkdownFences(text: String): String {
        var s = text.trim()
        if (s.startsWith("```json")) s = s.removePrefix("```json")
        else if (s.startsWith("```"))   s = s.removePrefix("```")
        if (s.endsWith("```"))          s = s.removeSuffix("```")
        return s.trim()
    }
}