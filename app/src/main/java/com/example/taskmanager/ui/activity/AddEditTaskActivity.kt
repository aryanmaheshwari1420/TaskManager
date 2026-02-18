package com.example.taskmanager.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.taskmanager.data.local.Task
import com.example.taskmanager.databinding.ActivityAddEditBinding
import com.example.taskmanager.ui.viewmodel.TaskViewModel

// Handles both Add and Edit — if a Task is passed via Intent, we're editing
class AddEditTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditBinding
    private val viewModel: TaskViewModel by viewModels()
    private var taskId = 0 // 0 = new task, >0 = existing task

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill fields if editing an existing task
        val task = intent.getSerializableExtra("task") as? Task
        task?.let {
            taskId = it.id
            binding.etTitle.setText(it.title)
            binding.etDescription.setText(it.description)
        }

        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString()
            val description = binding.etDescription.text.toString()

            if (title.isEmpty()) {
                Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newTask = Task(taskId, title, description)
            if (taskId == 0) viewModel.insert(newTask) else viewModel.update(newTask)
            finish()
        }
    }
}
