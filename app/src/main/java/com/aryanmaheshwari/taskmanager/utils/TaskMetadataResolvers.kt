package com.aryanmaheshwari.taskmanager.utils

/**
 * Utility responsible for resolving and normalizing task priority
 * across voice, AI, and manual task creation flows.
 */
object TaskPriorityResolver {
    const val DEFAULT_PRIORITY = "LOW"

    val SUPPORTED_PRIORITIES = setOf("LOW", "MEDIUM", "HIGH", "URGENT")

    /**
     * Resolves the final priority from an AI-parsed priority and the source text/transcript.
     * - Checks for explicit user priority phrases in English/Hinglish (takes precedence or falls back).
     * - Normalizes recognized priorities (LOW, MEDIUM, HIGH, URGENT).
     * - Defaults to "LOW" when omitted, null, blank, or unrecognized.
     */
    fun resolvePriority(aiPriority: String?, sourceText: String? = null): String {
        // First check explicit priority statements in source text/speech
        val explicitFromText = extractPriorityFromText(sourceText)
        if (explicitFromText != null) {
            return explicitFromText
        }

        // Check if AI provided a recognized priority
        val cleaned = aiPriority?.trim()?.uppercase()
        if (!cleaned.isNullOrBlank() && cleaned != "NULL") {
            when (cleaned) {
                "URGENT" -> return "URGENT"
                "HIGH" -> return "HIGH"
                "MEDIUM" -> return "MEDIUM"
                "LOW" -> return "LOW"
            }
        }

        return DEFAULT_PRIORITY
    }

    /**
     * Extracts explicitly stated priority from English and Hindi/Hinglish speech/text.
     * Guards against false positives (e.g., "important" inside an unrelated phrase).
     */
    fun extractPriorityFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()

        // 1. High / Urgent expressions
        val urgentRegex = Regex("""\b(urgent|urgently|emergency)\b""", RegexOption.IGNORE_CASE)
        val highPriorityRegex = Regex("""\bhigh[- ]priority(\s*(ka|ki|ke|hai))?\b""", RegexOption.IGNORE_CASE)
        val zarooriRegex = Regex("""\b(bahut|bohot|bada|zyada|jyada)\s+(zaroori|jaruri)\b|\b(zaroori|jaruri)\s+hai\b""", RegexOption.IGNORE_CASE)
        val explicitImportantRegex = Regex("""\b(priority\s+(is\s+)?important|important\s+(task|priority|reminder)|(yeh|ye|it['’]?s|is)\s+important)\b""", RegexOption.IGNORE_CASE)

        if (urgentRegex.containsMatchIn(lower) ||
            highPriorityRegex.containsMatchIn(lower) ||
            zarooriRegex.containsMatchIn(lower) ||
            explicitImportantRegex.containsMatchIn(lower)
        ) {
            return if (urgentRegex.containsMatchIn(lower)) "URGENT" else "HIGH"
        }

        // 2. Medium expressions
        val mediumRegex = Regex("""\b(medium|normal)[- ]priority(\s*(ka|ki|ke|hai))?\b""", RegexOption.IGNORE_CASE)
        if (mediumRegex.containsMatchIn(lower)) {
            return "MEDIUM"
        }

        // 3. Low expressions
        val lowRegex = Regex("""\b(low|kam)[- ]priority(\s*(ka|ki|ke|hai))?\b|\bkam\s+(zaroori|jaruri)\b""", RegexOption.IGNORE_CASE)
        if (lowRegex.containsMatchIn(lower)) {
            return "LOW"
        }

        return null
    }
}

/**
 * Utility responsible for cleaning, sanitizing, and resolving task descriptions.
 * Prevents literal "null" from ever being stored, and provides sensible fallbacks
 * without blindly copying entire reminder boilerplate or duplicating the title.
 */
object TaskDescriptionResolver {

    fun cleanText(text: String?): String {
        if (text == null) return ""
        val trimmed = text.trim()
        if (trimmed.equals("null", ignoreCase = true)) return ""
        return trimmed
    }

    /**
     * Resolves the task description:
     * - Returns valid cleaned raw description if present.
     * - If empty, attempts a sensible fallback extraction from source text.
     * - Never returns literal "null".
     */
    fun resolveDescription(
        rawDescription: String?,
        title: String,
        sourceText: String?
    ): String {
        val cleanRaw = cleanText(rawDescription)
        if (cleanRaw.isNotBlank()) {
            return cleanRaw
        }

        return deriveSensibleFallback(title, sourceText)
    }

    fun deriveSensibleFallback(title: String, sourceText: String?): String {
        val cleanedSource = cleanText(sourceText)
        if (cleanedSource.isBlank()) return ""

        val trimmedTitle = title.trim()

        // If the source text is identical to the title, do not duplicate
        if (cleanedSource.equals(trimmedTitle, ignoreCase = true)) {
            return ""
        }

        // Strip common reminder prefixes
        var extracted = cleanedSource
        val prefixes = listOf(
            Regex("""^(please\s+)?(remind\s+me(\s+to|\s+that|\s+about)?)\s+""", RegexOption.IGNORE_CASE),
            Regex("""^(please\s+)?(add|create)(\s+a)?\s+(task|reminder)(\s+to)?\s+""", RegexOption.IGNORE_CASE),
            Regex("""^(mujhe\s+)?(kal|aaj|parson)?(\s*\d{1,2}(\s*(baje|pm|am|:\d{2}))?)?\s*(yaad\s+dila\s*(dena|do|na))\s*,\s*""", RegexOption.IGNORE_CASE),
            Regex("""^(mujhe\s+)?(yaad\s+dila\s*(dena|do|na))\s*(ki\s*)?""", RegexOption.IGNORE_CASE)
        )

        for (pattern in prefixes) {
            extracted = extracted.replace(pattern, "").trim()
        }

        // Strip common reminder suffixes
        val suffixes = listOf(
            Regex("""\s*,\s*(mujhe\s+)?yaad\s+dila\s*(dena|do|na)\.?$""", RegexOption.IGNORE_CASE),
            Regex("""\s*(yaad\s+dila\s*(dena|do|na))\.?$""", RegexOption.IGNORE_CASE)
        )

        for (pattern in suffixes) {
            extracted = extracted.replace(pattern, "").trim()
        }

        // Strip title prefix if extracted starts with the title, to avoid duplicating it
        if (trimmedTitle.isNotBlank()) {
            val titlePrefixRegex = Regex("""^""" + Regex.escape(trimmedTitle) + """\s*([,:\-–—]\s*)?""", RegexOption.IGNORE_CASE)
            extracted = extracted.replace(titlePrefixRegex, "").trim()
        }

        if (extracted.isBlank() || extracted.equals(trimmedTitle, ignoreCase = true)) {
            return ""
        }

        // If remaining text is basically the title with Hindi auxiliary verbs or punctuation (e.g. "interview hai." vs "Interview")
        val strippedAux = extracted.replace(Regex("""\b(hai|h|tha|the)\b|[.,!?]""", RegexOption.IGNORE_CASE), "").trim()
        if (strippedAux.equals(trimmedTitle, ignoreCase = true) || strippedAux.isBlank()) {
            return ""
        }

        return extracted
    }
}
