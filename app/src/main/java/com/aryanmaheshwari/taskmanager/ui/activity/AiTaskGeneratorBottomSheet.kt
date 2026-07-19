package com.aryanmaheshwari.taskmanager.ui.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.aryanmaheshwari.taskmanager.R
import com.aryanmaheshwari.taskmanager.data.local.ChecklistItem
import com.aryanmaheshwari.taskmanager.data.local.GeneratedTask
import com.aryanmaheshwari.taskmanager.databinding.LayoutAiTaskGeneratorBottomSheetBinding
import com.aryanmaheshwari.taskmanager.ui.viewmodel.AiState
import com.aryanmaheshwari.taskmanager.ui.viewmodel.AiTaskViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Material Design 3 BottomSheetDialogFragment that drives the entire AI Task Generator flow:
 *
 *   Input panel  →  (Generate clicked)  →  Loading panel  →  Preview panel
 *
 * Business logic lives exclusively in [AiTaskViewModel].
 * UI observes LiveData and delegates all actions to the ViewModel.
 */
class AiTaskGeneratorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutAiTaskGeneratorBottomSheetBinding? = null
    private val binding get() = _binding!!

    // Shared with the host Activity so the task list refreshes after Save
    private val viewModel: AiTaskViewModel by activityViewModels()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutAiTaskGeneratorBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        expandSheet()
        setupSuggestionChips()
        setupInputPanelButtons()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.resetState()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // Sheet behaviour
    // -------------------------------------------------------------------------

    /** Expand the bottom sheet to 90 % screen height so the preview is fully readable. */
    private fun expandSheet() {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    // -------------------------------------------------------------------------
    // Input panel setup
    // -------------------------------------------------------------------------

    private fun setupSuggestionChips() {
        val suggestions = mapOf(
            binding.chipFlutter to "Prepare Flutter interview",
            binding.chipGoa     to "Plan Goa trip",
            binding.chipRoom    to "Organize my room",
            binding.chipAndroid to "Learn Android",
            binding.chipWedding to "Wedding planning"
        )
        suggestions.forEach { (chip, text) ->
            chip.setOnClickListener { binding.etPrompt.setText(text) }
        }
    }

    private fun setupInputPanelButtons() {
        binding.btnInputCancel.setOnClickListener { dismiss() }

        binding.btnGenerate.setOnClickListener {
            val prompt = binding.etPrompt.text?.toString().orEmpty().trim()
            if (prompt.isEmpty()) {
                binding.etPrompt.error = "Please enter a goal first"
                return@setOnClickListener
            }
            // Delegate entirely to ViewModel — no business logic here
            viewModel.generateTask(prompt)
        }
    }

    // -------------------------------------------------------------------------
    // ViewModel observation
    // -------------------------------------------------------------------------

    private fun observeViewModel() {
        viewModel.aiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AiState.Idle    -> showInputPanel()
                is AiState.Loading -> showLoadingPanel()
                is AiState.Success -> showPreviewPanel(state.task)
                is AiState.Error   -> {
                    showInputPanel()
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Panel visibility helpers
    // -------------------------------------------------------------------------

    private fun showInputPanel() {
        binding.panelInput.visibility   = View.VISIBLE
        binding.panelLoading.visibility = View.GONE
        binding.panelPreview.visibility = View.GONE
    }

    private fun showLoadingPanel() {
        binding.panelInput.visibility   = View.GONE
        binding.panelLoading.visibility = View.VISIBLE
        binding.panelPreview.visibility = View.GONE
    }

    private fun showPreviewPanel(task: GeneratedTask) {
        binding.panelInput.visibility   = View.GONE
        binding.panelLoading.visibility = View.GONE
        binding.panelPreview.visibility = View.VISIBLE

        populatePreview(task)
        setupPreviewButtons(task)
    }

    // -------------------------------------------------------------------------
    // Preview population
    // -------------------------------------------------------------------------

    private fun populatePreview(task: GeneratedTask) {
        // Title and description
        binding.tvPreviewTitle.text       = task.title
        binding.tvPreviewDescription.text = task.description

        // Priority chip — colour-coded for quick visual scanning
        binding.chipPriority.apply {
            text = task.priority.replaceFirstChar { it.uppercase() }
            val (bg, fg) = priorityColors(task.priority)
            chipBackgroundColor = ColorStateList.valueOf(bg)
            setTextColor(fg)
        }

        // Category chip — hidden when null
        if (task.category != null) {
            binding.chipCategory.apply {
                visibility = View.VISIBLE
                text = task.category.name
                chipBackgroundColor = ColorStateList.valueOf(
                    requireContext().getColor(R.color.primary_light)
                )
                setTextColor(requireContext().getColor(R.color.primary))
            }
        } else {
            binding.chipCategory.visibility = View.GONE
        }

        // Due date
        binding.tvDueDate.text = if (task.dueDate.isNotBlank()) task.dueDate else "No due date"

        // Checklist — inflate items programmatically
        buildChecklist(task.checklist)
    }

    private fun buildChecklist(items: List<ChecklistItem>) {
        binding.layoutChecklist.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        items.forEach { item ->
            val row = inflater.inflate(
                R.layout.item_checklist_preview,
                binding.layoutChecklist,
                false
            ) as CheckBox
            row.text         = item.text
            row.isChecked    = item.isChecked
            // Check-state changes are UI-only (item is a data class var)
            row.setOnCheckedChangeListener { _, checked -> item.isChecked = checked }
            // Small margin between items
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 2 }
            binding.layoutChecklist.addView(row, params)
        }
    }

    // -------------------------------------------------------------------------
    // Preview button wiring
    // -------------------------------------------------------------------------

    private fun setupPreviewButtons(task: GeneratedTask) {
        // Cancel — go back to input panel so user can regenerate
        binding.btnPreviewCancel.setOnClickListener {
            viewModel.resetState()
        }

        // Edit — open AddEditTaskActivity prefilled, then close the sheet
        binding.btnEdit.setOnClickListener {
            val formattedDesc = viewModel.buildFormattedDescription(task)
            val intent = Intent(requireContext(), AddEditTaskActivity::class.java).apply {
                putExtra("prefill_title", task.title)
                putExtra("prefill_description", formattedDesc)
            }
            startActivity(intent)
            dismiss()
        }

        // Save — delegate entirely to ViewModel, then close the sheet
        binding.btnSave.setOnClickListener {
            binding.btnSave.isEnabled = false
            viewModel.saveGeneratedTask(task) { success, message ->
                if (success) {
                    Toast.makeText(requireContext(), "Task saved!", Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    binding.btnSave.isEnabled = true
                    Toast.makeText(requireContext(), message ?: "Error saving task", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Priority colour mapping
    // -------------------------------------------------------------------------

    /** Returns a (background, foreground) colour pair for each priority level. */
    private fun priorityColors(priority: String): Pair<Int, Int> = when (priority.uppercase()) {
        "HIGH"   -> Color.parseColor("#FFEBE6") to Color.parseColor("#D63B00")
        "MEDIUM" -> Color.parseColor("#FFF7E6") to Color.parseColor("#B36A00")
        else     -> Color.parseColor("#E6F4EA") to Color.parseColor("#1E7A34") // LOW
    }

    companion object {
        const val TAG = "AiTaskGeneratorBottomSheet"
    }
}
