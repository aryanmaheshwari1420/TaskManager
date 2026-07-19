package com.aryanmaheshwari.taskmanager.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Request payload for the Gemini API content generation.
 */
data class GeminiRequest(
    @SerializedName("contents") val contents: List<Content>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig? = null
)

data class Content(
    @SerializedName("parts") val parts: List<Part>,
    @SerializedName("role") val role: String? = null
)

data class Part(
    @SerializedName("text") val text: String
)

data class GenerationConfig(
    @SerializedName("responseMimeType") val responseMimeType: String? = null
)

/**
 * Response payload returned from the Gemini API.
 */
data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<Candidate>?
)

data class Candidate(
    @SerializedName("content") val content: Content?,
    @SerializedName("finishReason") val finishReason: String?
)
