package com.aryanmaheshwari.taskmanager.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aryanmaheshwari.taskmanager.R
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.databinding.ItemTaskBinding
import com.aryanmaheshwari.taskmanager.utils.setCardFeedback
import com.aryanmaheshwari.taskmanager.utils.TaskPriorityResolver
import com.aryanmaheshwari.taskmanager.utils.TaskDescriptionResolver

class TaskAdapter(
    private val onEdit: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var taskList = listOf<Task>()

    fun setTasks(tasks: List<Task>) {
        taskList = tasks
        notifyDataSetChanged()
    }

    fun taskAt(position: Int): Task = taskList[position]

    inner class TaskViewHolder(val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun getItemCount() = taskList.size

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = taskList[position]
        holder.binding.tvTitle.text = task.title
        
        // Parse dynamic tags from description
        val parsedData = parseTaskDescription(task.description)
        holder.binding.tvDescription.text = parsedData.cleanDescription
        holder.binding.tvDescription.visibility = if (parsedData.cleanDescription.isNotBlank()) View.VISIBLE else View.GONE
        
        var hasTags = false
        val context = holder.itemView.context
        
        if (parsedData.priority != null) {
            holder.binding.tvPriorityBadge.text = parsedData.priority.uppercase()
            holder.binding.tvPriorityBadge.visibility = View.VISIBLE
            
            // Set contextual colors based on priority value
            when (parsedData.priority.uppercase()) {
                "URGENT", "HIGH" -> {
                    holder.binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.error))
                    holder.binding.tvPriorityBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.error_container)
                    )
                }
                "MEDIUM" -> {
                    holder.binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.warning))
                    holder.binding.tvPriorityBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.warning_container)
                    )
                }
                else -> {
                    holder.binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.info))
                    holder.binding.tvPriorityBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.info_container)
                    )
                }
            }
            hasTags = true
        } else {
            holder.binding.tvPriorityBadge.visibility = View.GONE
        }
        
        if (parsedData.category != null) {
            holder.binding.tvCategoryBadge.text = parsedData.category
            holder.binding.tvCategoryBadge.visibility = View.VISIBLE
            hasTags = true
        } else {
            holder.binding.tvCategoryBadge.visibility = View.GONE
        }
        
        if (parsedData.dueDate != null) {
            holder.binding.tvDueDateBadge.text = parsedData.dueDate
            holder.binding.tvDueDateBadge.visibility = View.VISIBLE
            hasTags = true
        } else {
            holder.binding.tvDueDateBadge.visibility = View.GONE
        }
        
        holder.binding.layoutTags.visibility = if (hasTags) View.VISIBLE else View.GONE
        
        holder.itemView.setCardFeedback()
        holder.itemView.setOnClickListener { onEdit(task) }
    }

    fun deleteAt(position: Int) {
        onDelete(taskList[position])
    }

    // ---------- Metadata Parser Helper ----------

    data class ParsedTaskData(
        val cleanDescription: String,
        val priority: String?,
        val category: String?,
        val dueDate: String?
    )

    private fun parseTaskDescription(description: String): ParsedTaskData {
        var priority: String? = null
        var category: String? = null
        var dueDate: String? = null
        
        val lines = description.split("\n")
        val cleanLines = mutableListOf<String>()
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Priority:") -> {
                    val rawP = trimmed.substringAfter("Priority:").trim()
                    if (rawP.isNotBlank() && !rawP.equals("null", ignoreCase = true)) {
                        priority = rawP.uppercase()
                    }
                }
                trimmed.startsWith("Category:") -> {
                    val rawC = trimmed.substringAfter("Category:").trim()
                    if (rawC.isNotBlank() && !rawC.equals("null", ignoreCase = true)) {
                        category = rawC
                    }
                }
                trimmed.startsWith("Due:") -> {
                    val rawD = trimmed.substringAfter("Due:").trim()
                    if (rawD.isNotBlank() && !rawD.equals("null", ignoreCase = true)) {
                        dueDate = rawD
                    }
                }
                trimmed.startsWith("Checklist:") || trimmed.startsWith("•") -> {
                    // Skip these lines in the clean description preview
                }
                trimmed.equals("null", ignoreCase = true) -> {
                    // Skip legacy literal "null"
                }
                else -> {
                    cleanLines.add(line)
                }
            }
        }
        
        val cleanDesc = TaskDescriptionResolver.cleanText(cleanLines.joinToString("\n"))
        val resolvedPriority = TaskPriorityResolver.resolvePriority(priority, null)
        return ParsedTaskData(cleanDesc, resolvedPriority, category, dueDate)
    }
}