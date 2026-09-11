package com.aryanmaheshwari.taskmanager.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Request payload for the Gemini API content generation.
 * Supports both text and audio/inline data inputs.
 */
data class GeminiRequest(
    @SerializedName("contents") val contents: List<Content> = emptyList(),
    @SerializedName("generationConfig") val generationConfig: GenerationConfig? = null
)

data class Content(
    @SerializedName("parts") val parts: List<Part> = emptyList(),
    @SerializedName("role") val role: String? = null
)

/**
 * Represents a single part of the request.
 * Can be either text or inline_data (for audio/images).
 *
 * For text: Part(text = "user input")
 * For audio: Part(inlineData = InlineData(mimeType = "audio/mp3", data = base64String))
 */
data class Part(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: InlineData? = null
)

/**
 * Inline data for audio, images, or other media.
 * Used to send binary data to Gemini API.
 *
 * @param mimeType MIME type of the data (e.g., "audio/mp3", "audio/wav")
 * @param data Base64-encoded binary data
 */
data class InlineData(
    @SerializedName("mimeType") val mimeType: String = "",
    @SerializedName("data") val data: String = ""
)

data class GenerationConfig(
    @SerializedName("responseMimeType") val responseMimeType: String? = null
)

/**
 * Response payload returned from the Gemini API.
 */
data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<Candidate>? = null
)

data class Candidate(
    @SerializedName("content") val content: Content? = null,
    @SerializedName("finishReason") val finishReason: String? = null
)