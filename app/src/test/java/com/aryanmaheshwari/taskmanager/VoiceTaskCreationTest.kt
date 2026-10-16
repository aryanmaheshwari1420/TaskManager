package com.aryanmaheshwari.taskmanager

import com.aryanmaheshwari.taskmanager.data.local.CategoryEntity
import com.aryanmaheshwari.taskmanager.data.local.ChecklistItem
import com.aryanmaheshwari.taskmanager.data.local.GeneratedTask
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.data.local.toAiSnapshot
import com.aryanmaheshwari.taskmanager.data.local.toGeneratedTask
import com.aryanmaheshwari.taskmanager.utils.TaskDescriptionResolver
import com.aryanmaheshwari.taskmanager.utils.TaskPriorityResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTaskCreationTest {

    // -------------------------------------------------------------------------
    // 1. Title Preservation
    // -------------------------------------------------------------------------
    @Test
    fun voiceCreatedTask_retainsExtractedTitle() {
        val extractedTitle = "Interview"
        val transcript = "Mujhe kal 5 baje yaad dila dena, interview hai."
        val task = GeneratedTask(
            title = extractedTitle,
            description = TaskDescriptionResolver.resolveDescription(null, extractedTitle, transcript),
            priority = TaskPriorityResolver.resolvePriority(null, transcript),
            category = null,
            dueDate = "2026-10-17 17:00",
            checklist = emptyList(),
            transcript = transcript
        )

        assertEquals("Interview", task.title)
    }

    // -------------------------------------------------------------------------
    // 2. Valid Description Not Overwritten by Null
    // -------------------------------------------------------------------------
    @Test
    fun voiceCreatedTask_descriptionNotOverwritten_whenValidDescriptionExists() {
        val title = "Submit Report"
        val validDescription = "Prepare the quarterly analytics summary for team lead."
        val transcript = "Submit report tomorrow, prepare the quarterly analytics summary for team lead."

        val resolved = TaskDescriptionResolver.resolveDescription(
            rawDescription = validDescription,
            title = title,
            sourceText = transcript
        )

        assertEquals("Prepare the quarterly analytics summary for team lead.", resolved)
    }

    // -------------------------------------------------------------------------
    // 3. Missing Description Handled Safely (Never saves literal "null")
    // -------------------------------------------------------------------------
    @Test
    fun voiceCreatedTask_missingDescriptionHandledSafely_neverSavesLiteralNull() {
        val title = "Interview"
        val transcript = "Mujhe kal 5 baje yaad dila dena, interview hai."

        // Case A: rawDescription is null
        val resolvedFromNull = TaskDescriptionResolver.resolveDescription(
            rawDescription = null,
            title = title,
            sourceText = transcript
        )
        assertFalse("Resolved description must not contain literal 'null'", resolvedFromNull.contains("null"))
        assertFalse("Resolved description must not be 'null'", resolvedFromNull.equals("null", ignoreCase = true))

        // Case B: rawDescription is the literal string "null" (common JSON parsing artifact)
        val resolvedFromLiteralNull = TaskDescriptionResolver.resolveDescription(
            rawDescription = "null",
            title = title,
            sourceText = transcript
        )
        assertFalse("Resolved description must not contain literal 'null'", resolvedFromLiteralNull.contains("null"))
        assertFalse("Resolved description must not be 'null'", resolvedFromLiteralNull.equals("null", ignoreCase = true))

        // Case C: Formatting into database Task description does not include literal "null"
        val generatedTask = GeneratedTask(
            title = title,
            description = resolvedFromLiteralNull,
            priority = "LOW",
            category = null,
            dueDate = "Tomorrow at 5:00 PM",
            checklist = emptyList(),
            transcript = transcript
        )

        val cleanDesc = TaskDescriptionResolver.cleanText(generatedTask.description)
        val formattedDbDesc = buildString {
            if (cleanDesc.isNotBlank()) {
                append(cleanDesc)
                append("\n\n")
            }
            append("Priority: ${generatedTask.priority}\n")
            if (generatedTask.dueDate.isNotBlank()) {
                append("Due: ${generatedTask.dueDate}\n")
            }
        }.trim()

        assertFalse("DB description must never contain 'null'", formattedDbDesc.contains("null"))
        assertTrue("DB description must contain Priority: LOW", formattedDbDesc.contains("Priority: LOW"))
    }

    @Test
    fun voiceCreatedTask_doesNotBlindlyDumpFullReminderInstructionOrDuplicateTitle() {
        val title = "Interview"
        val transcript = "Mujhe kal 5 baje yaad dila dena, interview hai."

        val fallback = TaskDescriptionResolver.deriveSensibleFallback(title, transcript)
        // Since transcript details ("interview hai") is identical to title ("Interview"),
        // it must not duplicate the title as description.
        assertEquals("", fallback)
    }

    @Test
    fun voiceCreatedTask_extractsMeaningfulDetails_whenAdditionalContextExists() {
        val title = "Submit project"
        val transcript = "Please remind me to submit project, ensure all unit tests pass."

        val fallback = TaskDescriptionResolver.deriveSensibleFallback(title, transcript)
        assertEquals("ensure all unit tests pass.", fallback)
    }

    // -------------------------------------------------------------------------
    // 4. Missing Priority Defaults to LOW
    // -------------------------------------------------------------------------
    @Test
    fun missingPriority_defaultsToLow() {
        val prompt = "Remind me tomorrow at 5 PM about my interview."
        val resolved = TaskPriorityResolver.resolvePriority(aiPriority = null, sourceText = prompt)
        assertEquals("LOW", resolved)
    }

    // -------------------------------------------------------------------------
    // 5. Explicit HIGH Priority Preserved
    // -------------------------------------------------------------------------
    @Test
    fun explicitHighPriority_isPreserved() {
        // From AI priority tag
        assertEquals("HIGH", TaskPriorityResolver.resolvePriority(aiPriority = "HIGH", sourceText = "Submit assignment"))

        // From English user speech
        val speech = "Create a high-priority task to submit my assignment tomorrow."
        assertEquals("HIGH", TaskPriorityResolver.resolvePriority(aiPriority = null, sourceText = speech))

        // From Hindi/Hinglish "bahut zaroori"
        val hindiSpeech = "Mujhe kal 5 baje yaad dila dena, yeh task bahut zaroori hai."
        assertEquals("HIGH", TaskPriorityResolver.resolvePriority(aiPriority = null, sourceText = hindiSpeech))
    }

    @Test
    fun explicitUrgentPriority_isPreserved() {
        val speech = "Add an urgent task to fix the production crash."
        assertEquals("URGENT", TaskPriorityResolver.resolvePriority(aiPriority = null, sourceText = speech))
    }

    // -------------------------------------------------------------------------
    // 6. Explicit MEDIUM Priority Preserved
    // -------------------------------------------------------------------------
    @Test
    fun explicitMediumPriority_isPreserved() {
        // From AI priority tag
        assertEquals("MEDIUM", TaskPriorityResolver.resolvePriority(aiPriority = "MEDIUM", sourceText = "Call client"))

        // From user speech
        val speech = "Remind me to call the client, medium priority."
        assertEquals("MEDIUM", TaskPriorityResolver.resolvePriority(aiPriority = null, sourceText = speech))
    }

    // -------------------------------------------------------------------------
    // 7. Explicit LOW Priority Preserved
    // -------------------------------------------------------------------------
    @Test
    fun explicitLowPriority_isPreserved() {
        // From AI priority tag
        assertEquals("LOW", TaskPriorityResolver.resolvePriority(aiPriority = "LOW", sourceText = "Water plants"))

        // From English user speech
        val speech = "Water the plants tomorrow, low priority."
        assertEquals("LOW", TaskPriorityResolver.resolvePriority(aiPriority = null, sourceText = speech))

        // From Hinglish user speech
        val hinglishSpeech = "Yeh kaam kam priority ka hai, shaam ko yaad dila dena."
        assertEquals("LOW", TaskPriorityResolver.resolvePriority(aiPriority = null, sourceText = hinglishSpeech))
    }

    // -------------------------------------------------------------------------
    // 8. Null, Blank, or Invalid Priority Falls Back to LOW
    // -------------------------------------------------------------------------
    @Test
    fun nullBlankOrInvalidPriority_fallsBackToLow() {
        assertEquals("LOW", TaskPriorityResolver.resolvePriority(aiPriority = null, sourceText = null))
        assertEquals("LOW", TaskPriorityResolver.resolvePriority(aiPriority = "", sourceText = ""))
        assertEquals("LOW", TaskPriorityResolver.resolvePriority(aiPriority = "   ", sourceText = "No priority here"))
        assertEquals("LOW", TaskPriorityResolver.resolvePriority(aiPriority = "null", sourceText = "Normal reminder"))
        assertEquals("LOW", TaskPriorityResolver.resolvePriority(aiPriority = "UNKNOWN_PRIORITY", sourceText = null))
        assertEquals("LOW", TaskPriorityResolver.resolvePriority(aiPriority = "RANDOM_STRING", sourceText = "Random text"))
    }

    // -------------------------------------------------------------------------
    // False Positive Prevention
    // -------------------------------------------------------------------------
    @Test
    fun priorityResolver_avoidsFalsePositiveOnImportantWordsInContent() {
        // "important" used as an adjective describing a noun in the task content, not stating task priority
        val speech = "Please bring the important documents for the interview."
        val resolved = TaskPriorityResolver.resolvePriority(aiPriority = null, sourceText = speech)
        assertEquals("LOW", resolved)
    }

    // -------------------------------------------------------------------------
    // 9. Existing Task Creation Behavior Remains Intact
    // -------------------------------------------------------------------------
    @Test
    fun existingTaskCreation_andAiSnapshotMapping_remainsIntact() {
        val task = GeneratedTask(
            title = "Project Planning",
            description = "Detailed project milestone plan",
            priority = "HIGH",
            category = CategoryEntity("Work"),
            dueDate = "Next Friday",
            checklist = listOf(ChecklistItem(taskId = 0, text = "Scope requirements")),
            transcript = "High priority plan project"
        )

        // Convert to AiSnapshot and back
        val snapshot = task.toAiSnapshot(prompt = task.transcript!!, isVoice = true, audioFilePath = null, isPending = false)
        assertEquals("Project Planning", snapshot.title)
        assertEquals("Detailed project milestone plan", snapshot.description)
        assertEquals("HIGH", snapshot.priority)
        assertEquals("Work", snapshot.categoryName)

        val restoredTask = snapshot.toGeneratedTask()
        assertEquals("Project Planning", restoredTask.title)
        assertEquals("Detailed project milestone plan", restoredTask.description)
        assertEquals("HIGH", restoredTask.priority)
        assertEquals("Work", restoredTask.category?.name)
        assertEquals("Next Friday", restoredTask.dueDate)
        assertEquals(1, restoredTask.checklist.size)
        assertEquals("Scope requirements", restoredTask.checklist[0].text)

        // Verify Task Room Entity creation
        val dbTask = Task(id = 1, title = restoredTask.title, description = restoredTask.description)
        assertEquals(1, dbTask.id)
        assertEquals("Project Planning", dbTask.title)
        assertEquals("Detailed project milestone plan", dbTask.description)
    }
}
