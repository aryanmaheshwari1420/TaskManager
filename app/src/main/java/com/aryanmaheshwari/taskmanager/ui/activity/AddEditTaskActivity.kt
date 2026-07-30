package com.aryanmaheshwari.taskmanager.ui.activity

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.aryanmaheshwari.taskmanager.data.local.Task
import com.aryanmaheshwari.taskmanager.databinding.ActivityAddEditBinding
import com.aryanmaheshwari.taskmanager.ui.viewmodel.TaskViewModel

class AddEditTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditBinding
    private val viewModel: TaskViewModel by viewModels()
    private var taskId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val task = intent.getSerializableExtra("task") as? Task
        task?.let {
            taskId = it.id
            binding.etTitle.setText(it.title)
            binding.etDescription.setText(it.description)
        } ?: run {
            val prefillTitle = intent.getStringExtra("prefill_title")
            val prefillDesc = intent.getStringExtra("prefill_description")
            if (!prefillTitle.isNullOrBlank()) {
                binding.etTitle.setText(prefillTitle)
                binding.etDescription.setText(prefillDesc ?: "")
            }
        }

        // Clear the inline error the moment the user starts fixing it
        binding.etTitle.addTextChangedListener {
            if (!it.isNullOrBlank()) binding.tilTitle.error = null
        }

        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString()
            val description = binding.etDescription.text.toString()

            if (title.isEmpty()) {
                binding.tilTitle.error = "Title cannot be empty"
                return@setOnClickListener
            }
            binding.tilTitle.error = null

            val newTask = Task(taskId, title, description)
            if (taskId == 0) viewModel.insert(newTask) else viewModel.update(newTask)
            finish()
        }
    }
}