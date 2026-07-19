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
import java.util.concurrent.TimeUnit
import kotlin.getValue

/**
 * Service layer that communicates with Google's Gemini REST API via Retrofit + OkHttp.
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

    // ---------- Prompt -------------------------------------------------------

    private val systemInstruction = """
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
     * Generates a fully structured [GeneratedTask] from a user prompt.
     * Throws descriptive exceptions on network or parsing failures.
     */
    suspend fun generateTask(prompt: String): GeneratedTask {
        require(apiKey.isNotBlank()) {
            "Gemini API key is not configured. Add gemini.api.key to local.properties."
        }

        val fullPrompt = "$systemInstruction\n\nUser goal: $prompt"

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
