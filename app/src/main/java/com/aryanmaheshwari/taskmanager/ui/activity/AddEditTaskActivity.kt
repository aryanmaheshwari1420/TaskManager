package com.aryanmaheshwari.taskmanager.ui.activity

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.aryanmaheshwari.taskmanager.R
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.databinding.ActivityAddEditBinding
import com.aryanmaheshwari.taskmanager.ui.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddEditTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditBinding
    private val viewModel: TaskViewModel by viewModels()
    private var taskId = 0

    // Selected metadata states
    private var selectedPriority: String? = null
    private var selectedCategory: String? = null
    private var selectedDueDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Back Navigation
        binding.btnBack.setOnClickListener { finish() }

        // 2. Load Task details (if editing) or Prefill
        val task = intent.getSerializableExtra("task") as? Task
        task?.let {
            taskId = it.id
            binding.etTitle.setText(it.title)
            
            // Parse existing task metadata from description
            val parsed = parseTaskDescription(it.description)
            binding.etDescription.setText(parsed.cleanDescription)
            
            selectedCategory = parsed.category
            if (selectedCategory != null) {
                binding.tvSelectedCategory.text = selectedCategory
                binding.tvSelectedCategory.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
            
            selectedDueDate = parsed.dueDate
            if (selectedDueDate != null) {
                binding.tvSelectedDueDate.text = selectedDueDate
                binding.tvSelectedDueDate.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
            
            selectedPriority = parsed.priority?.uppercase()
            selectPriority(selectedPriority)

            // Setup Delete Task trigger shown on Screen C
            binding.btnDelete.visibility = View.VISIBLE
            binding.btnDelete.setOnClickListener {
                viewModel.delete(task)
                Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        } ?: run {
            val prefillTitle = intent.getStringExtra("prefill_title")
            val prefillDesc = intent.getStringExtra("prefill_description")
            if (!prefillTitle.isNullOrBlank()) {
                binding.etTitle.setText(prefillTitle)
                val parsed = parseTaskDescription(prefillDesc ?: "")
                binding.etDescription.setText(parsed.cleanDescription)
                selectedCategory = parsed.category
                selectedDueDate = parsed.dueDate
                selectedPriority = parsed.priority?.uppercase()
                
                if (selectedCategory != null) {
                    binding.tvSelectedCategory.text = selectedCategory
                    binding.tvSelectedCategory.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                }
                if (selectedDueDate != null) {
                    binding.tvSelectedDueDate.text = selectedDueDate
                    binding.tvSelectedDueDate.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                }
                selectPriority(selectedPriority)
            }
        }

        // 3. Setup Character Counters and Validation
        binding.etTitle.addTextChangedListener {
            val count = it?.length ?: 0
            binding.tvTitleCount.text = "$count/100"
            if (!it.isNullOrBlank()) binding.tilTitle.error = null
        }

        binding.etDescription.addTextChangedListener {
            val count = it?.length ?: 0
            binding.tvDescriptionCount.text = "$count/500"
        }

        // 4. Setup Priority Toggles
        binding.cardPriorityLow.setOnClickListener { togglePriority("LOW") }
        binding.cardPriorityMedium.setOnClickListener { togglePriority("MEDIUM") }
        binding.cardPriorityHigh.setOnClickListener { togglePriority("HIGH") }
        binding.cardPriorityUrgent.setOnClickListener { togglePriority("URGENT") }

        // 5. Setup List/Category Selector Popup Menu
        val categories = listOf("Work", "Personal", "Learning", "Shopping", "Health")
        binding.cardCategorySelector.setOnClickListener {
            val popup = PopupMenu(this, binding.cardCategorySelector)
            categories.forEach { popup.menu.add(it) }
            popup.menu.add("None")
            popup.setOnMenuItemClickListener { menuItem ->
                val selected = menuItem.title.toString()
                if (selected == "None") {
                    selectedCategory = null
                    binding.tvSelectedCategory.text = "Select a list"
                    binding.tvSelectedCategory.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                } else {
                    selectedCategory = selected
                    binding.tvSelectedCategory.text = selected
                    binding.tvSelectedCategory.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                }
                true
            }
            popup.show()
        }

        // 6. Setup Due Date Picker Dialog
        binding.cardDueDateSelector.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selectedCal = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth)
                    }
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val formatted = sdf.format(selectedCal.time)
                    selectedDueDate = formatted
                    binding.tvSelectedDueDate.text = formatted
                    binding.tvSelectedDueDate.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Clear") { _, _ ->
                selectedDueDate = null
                binding.tvSelectedDueDate.text = "Select date"
                binding.tvSelectedDueDate.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
            datePickerDialog.show()
        }

        // 7. Save / Update Click Action
        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val userDescription = binding.etDescription.text.toString().trim()

            if (title.isEmpty()) {
                binding.tilTitle.error = "Title cannot be empty"
                return@setOnClickListener
            }
            binding.tilTitle.error = null

            // Construct DB description containing parsed tags
            val fullDescription = buildTaskDescription(userDescription, selectedPriority, selectedCategory, selectedDueDate)

            val newTask = Task(taskId, title, fullDescription)
            if (taskId == 0) viewModel.insert(newTask) else viewModel.update(newTask)
            finish()
        }
    }

    // ---------- Helper Methods ----------

    private fun togglePriority(priority: String) {
        selectedPriority = if (selectedPriority == priority) null else priority
        selectPriority(selectedPriority)
    }

    private fun selectPriority(priority: String?) {
        val defaultBg = ContextCompat.getColor(this, R.color.bg_main)
        val dividerColor = ContextCompat.getColor(this, R.color.divider)
        val defaultStroke = (1 * resources.displayMetrics.density).toInt()

        // Reset backgrounds and strokes
        binding.cardPriorityLow.setCardBackgroundColor(defaultBg)
        binding.cardPriorityLow.strokeColor = dividerColor
        binding.cardPriorityLow.strokeWidth = defaultStroke

        binding.cardPriorityMedium.setCardBackgroundColor(defaultBg)
        binding.cardPriorityMedium.strokeColor = dividerColor
        binding.cardPriorityMedium.strokeWidth = defaultStroke

        binding.cardPriorityHigh.setCardBackgroundColor(defaultBg)
        binding.cardPriorityHigh.strokeColor = dividerColor
        binding.cardPriorityHigh.strokeWidth = defaultStroke

        binding.cardPriorityUrgent.setCardBackgroundColor(defaultBg)
        binding.cardPriorityUrgent.strokeColor = dividerColor
        binding.cardPriorityUrgent.strokeWidth = defaultStroke

        val activeStroke = (1.5f * resources.displayMetrics.density).toInt()

        when (priority) {
            "LOW" -> {
                binding.cardPriorityLow.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success_container))
                binding.cardPriorityLow.strokeColor = ContextCompat.getColor(this, R.color.success)
                binding.cardPriorityLow.strokeWidth = activeStroke
            }
            "MEDIUM" -> {
                binding.cardPriorityMedium.setCardBackgroundColor(ContextCompat.getColor(this, R.color.warning_container))
                binding.cardPriorityMedium.strokeColor = ContextCompat.getColor(this, R.color.warning)
                binding.cardPriorityMedium.strokeWidth = activeStroke
            }
            "HIGH" -> {
                binding.cardPriorityHigh.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error_container))
                binding.cardPriorityHigh.strokeColor = ContextCompat.getColor(this, R.color.error)
                binding.cardPriorityHigh.strokeWidth = activeStroke
            }
            "URGENT" -> {
                binding.cardPriorityUrgent.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_light))
                binding.cardPriorityUrgent.strokeColor = ContextCompat.getColor(this, R.color.primary)
                binding.cardPriorityUrgent.strokeWidth = activeStroke
            }
        }
    }

    data class ParsedFields(
        val cleanDescription: String,
        val priority: String?,
        val category: String?,
        val dueDate: String?
    )

    private fun parseTaskDescription(description: String): ParsedFields {
        var priority: String? = null
        var category: String? = null
        var dueDate: String? = null
        val cleanLines = mutableListOf<String>()

        val lines = description.split("\n")
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Priority:") -> {
                    priority = trimmed.substringAfter("Priority:").trim()
                }
                trimmed.startsWith("Category:") -> {
                    category = trimmed.substringAfter("Category:").trim()
                }
                trimmed.startsWith("Due:") -> {
                    dueDate = trimmed.substringAfter("Due:").trim()
                }
                else -> {
                    cleanLines.add(line)
                }
            }
        }
        val cleanDesc = cleanLines.joinToString("\n").trim()
        return ParsedFields(cleanDesc, priority, category, dueDate)
    }

    private fun buildTaskDescription(
        userDescription: String,
        priority: String?,
        category: String?,
        dueDate: String?
    ): String {
        val builder = java.lang.StringBuilder()
        builder.append(userDescription.trim())

        if (priority != null || category != null || dueDate != null) {
            if (userDescription.isNotBlank()) {
                builder.append("\n\n")
            }
            if (priority != null) {
                builder.append("Priority: $priority\n")
            }
            if (category != null) {
                builder.append("Category: $category\n")
            }
            if (dueDate != null) {
                builder.append("Due: $dueDate\n")
            }
        }
        return builder.toString().trim()
    }
}