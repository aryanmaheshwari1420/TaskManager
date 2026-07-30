package com.aryanmaheshwari.taskmanager.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.databinding.ItemTaskBinding

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
        holder.binding.tvDescription.text = task.description
        holder.itemView.setOnClickListener { onEdit(task) }
    }

    // Called by ItemTouchHelper when a swipe completes — routes to the same
    // onDelete callback that used to fire from long-press.
    fun deleteAt(position: Int) {
        onDelete(taskList[position])
    }
}