package com.aryanmaheshwari.taskmanager.ui.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.aryanmaheshwari.taskmanager.R
import com.aryanmaheshwari.taskmanager.data.local.ChecklistItem
import com.aryanmaheshwari.taskmanager.data.local.GeneratedTask
import com.aryanmaheshwari.taskmanager.databinding.LayoutAiTaskGeneratorBottomSheetBinding
import com.aryanmaheshwari.taskmanager.ui.viewmodel.AiState
import com.aryanmaheshwari.taskmanager.ui.viewmodel.AiTaskViewModel
import com.aryanmaheshwari.taskmanager.ui.viewmodel.RecordingState
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.animation.DecelerateInterpolator
import com.aryanmaheshwari.taskmanager.utils.ViewInteractionAnimator
import com.aryanmaheshwari.taskmanager.utils.setClickFeedback
import com.aryanmaheshwari.taskmanager.utils.setCardFeedback
import com.aryanmaheshwari.taskmanager.utils.setIconButtonFeedback
import androidx.recyclerview.widget.LinearLayoutManager
import com.aryanmaheshwari.taskmanager.ui.adapter.AiSnapshotAdapter
import com.aryanmaheshwari.taskmanager.data.local.AiSnapshot

/**
 * Material Design 3 BottomSheetDialogFragment that drives the entire AI Task Generator flow.
 * Supports both text input and voice input (speech-to-task).
 */
class AiTaskGeneratorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutAiTaskGeneratorBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var skeletonAnimator: android.animation.ObjectAnimator? = null
    private lateinit var snapshotAdapter: AiSnapshotAdapter

    private val viewModel: AiTaskViewModel by activityViewModels()

    companion object {
        const val TAG = "AiTaskGeneratorBottomSheet"
        private const val PERMISSION_REQUEST_CODE = 42
    }

    override fun getTheme(): Int = R.style.ThemeOverlay_TM_BottomSheet

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
        setupSavedPlansRecyclerView()
        observeViewModel()
    }

    private fun expandSheet() {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun startSkeletonPulse() {
        // Obsolete: pulse skeleton lines replaced by dynamic progress views
    }

    private fun stopSkeletonPulse() {
        // Obsolete
    }

    private fun animatePanelTransition(showView: View, hideViews: List<View>) {
        showView.alpha = 0f
        showView.visibility = View.VISIBLE
        showView.animate().alpha(1f).setDuration(300).start()
        
        hideViews.forEach { view ->
            if (view.visibility == View.VISIBLE) {
                view.animate().alpha(0f).setDuration(200).withEndAction {
                    view.visibility = View.GONE
                    view.alpha = 1f
                }.start()
            } else {
                view.visibility = View.GONE
            }
        }
    }

    private fun showInputPanel() {
        binding.btnGenerate.isEnabled = true
        animatePanelTransition(binding.panelInput, listOf(binding.panelLoading, binding.panelPreview))
        stopSkeletonPulse()
        updateRecordingUI(RecordingState.Idle)
    }

    private fun showLoadingPanel() {
        binding.btnGenerate.isEnabled = false // Disable to prevent duplicate requests
        animatePanelTransition(binding.panelLoading, listOf(binding.panelInput, binding.panelPreview))
        val prompt = binding.etPrompt.text?.toString().orEmpty().trim()
        if (prompt.isNotBlank()) {
            binding.tvLoadingPrompt.text = "Planning \"$prompt\"..."
        } else {
            binding.tvLoadingPrompt.text = "Planning your task flow..."
        }
    }

    private fun showPreviewPanel(task: GeneratedTask) {
        animatePanelTransition(binding.panelPreview, listOf(binding.panelInput, binding.panelLoading))
        stopSkeletonPulse()
        populatePreview(task)
        setupPreviewButtons(task)
    }

    private fun setupSuggestionChips() {
        val suggestions = mapOf(
            binding.chipFlutter to "Prepare Flutter interview",
            binding.chipGoa     to "Plan Goa trip",
            binding.chipRoom    to "Organize my room",
            binding.chipAndroid to "Learn Android",
            binding.chipWedding to "Wedding planning"
        )
        suggestions.forEach { (chip, text) ->
            chip.setCardFeedback()
            chip.setOnClickListener { binding.etPrompt.setText(text) }
        }
    }

    private fun setupInputPanelButtons() {
        // Setup click visual feedback
        binding.btnInputCancel.setClickFeedback()
        binding.btnGenerate.setClickFeedback()
        binding.btnMicrophone.setIconButtonFeedback()
        binding.btnStopRecording.setIconButtonFeedback()
        binding.btnCancelRecording.setIconButtonFeedback()
        binding.btnEditSpeech.setClickFeedback()

        binding.btnInputCancel.setOnClickListener { dismiss() }

        binding.btnGenerate.setOnClickListener {
            val prompt = binding.etPrompt.text?.toString().orEmpty().trim()
            if (prompt.isEmpty()) {
                binding.etPrompt.error = "Please enter a goal first"
                return@setOnClickListener
            }
            viewModel.generateTask(prompt)
        }

        binding.btnMicrophone.setOnClickListener {
            requestMicrophonePermissionAndRecord()
        }

        binding.btnStopRecording.setOnClickListener {
            Log.d(TAG, "Tick button clicked - stopping recording")
            viewModel.stopRecordingAndProcess()
        }

        binding.btnCancelRecording.setOnClickListener {
            viewModel.cancelRecording()
        }

        binding.btnEditSpeech.setOnClickListener {
            val originalText = binding.tvUserSpeechText.text.toString()
            binding.etPrompt.setText(originalText)
            showInputPanel()
        }
    }

    private fun requestMicrophonePermissionAndRecord() {
        val permission = android.Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(requireContext(), permission)
            == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            requestPermissions(arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    private fun startRecording() {
        if (viewModel.startRecording()) {
            Toast.makeText(requireContext(), "Recording started. Speak now.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Failed to start recording.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(requireContext(), "Microphone permission denied.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.aiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AiState.Idle    -> showInputPanel()
                is AiState.Loading -> showLoadingPanel()
                is AiState.Success -> showPreviewPanel(state.task)
                is AiState.Error   -> {
                    Log.e(TAG, "UI received AiState.Error: ${state.message}")
                    showInputPanel()
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.recordingState.observe(viewLifecycleOwner) { state ->
            updateRecordingUI(state)
        }

        viewModel.isOnline.observe(viewLifecycleOwner) { isOnline ->
            if (isOnline) {
                binding.tvConnectivityBanner.visibility = View.GONE
                binding.tvVoiceLabel.text = "Or extract task using voice (Hindi/English)"
                if (viewModel.recordingState.value == RecordingState.Idle) {
                    binding.btnMicrophone.alpha = 1.0f
                    binding.btnMicrophone.isClickable = true
                }
            } else {
                binding.tvConnectivityBanner.visibility = View.VISIBLE
                binding.tvVoiceLabel.text = "Voice recording works offline; processing requires internet."
            }
        }

        viewModel.allAiSnapshots.observe(viewLifecycleOwner) { snapshots ->
            if (snapshots.isNullOrEmpty()) {
                binding.tvSavedPlansHeader.visibility = View.GONE
                binding.rvSavedPlans.visibility = View.GONE
            } else {
                binding.tvSavedPlansHeader.visibility = View.VISIBLE
                binding.rvSavedPlans.visibility = View.VISIBLE
                snapshotAdapter.setSnapshots(snapshots)
            }
        }
    }

    private fun setupSavedPlansRecyclerView() {
        snapshotAdapter = AiSnapshotAdapter(
            onClick = { snapshot ->
                if (snapshot.isPending) {
                    if (viewModel.isOnline.value == true) {
                        viewModel.retryPendingSnapshot(snapshot) {
                            Toast.makeText(requireContext(), "No internet connection to retry.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "You are currently offline. Please connect to internet to generate.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.loadSnapshotPreview(snapshot)
                }
            },
            onDelete = { snapshot ->
                viewModel.deleteSnapshot(snapshot)
            }
        )
        binding.rvSavedPlans.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = snapshotAdapter
        }
    }

    private var recordingAnimator: android.view.ViewPropertyAnimator? = null

    private fun startRecordingPulse() {
        binding.layoutRecordingIndicator.alpha = 1f
        fun pulse() {
            if (viewModel.recordingState.value == RecordingState.Recording) {
                recordingAnimator = binding.layoutRecordingIndicator.animate()
                    .alpha(if (binding.layoutRecordingIndicator.alpha > 0.6f) 0.3f else 1f)
                    .setDuration(600)
                    .withEndAction { pulse() }
                recordingAnimator?.start()
            } else {
                binding.layoutRecordingIndicator.alpha = 1f
            }
        }
        pulse()
    }

    private fun stopRecordingPulse() {
        recordingAnimator?.cancel()
        recordingAnimator = null
        binding.layoutRecordingIndicator.alpha = 1f
    }

    private fun updateRecordingUI(state: RecordingState) {
        when (state) {
            RecordingState.Idle -> {
                stopRecordingPulse()
                binding.btnMicrophone.visibility = View.VISIBLE
                binding.btnMicrophone.isEnabled = true
                binding.layoutRecordingIndicator.visibility = View.GONE
                binding.btnStopRecording.visibility = View.GONE
                binding.btnCancelRecording.visibility = View.GONE
                binding.etPrompt.isEnabled = true
                binding.btnGenerate.isEnabled = true
            }
            RecordingState.Recording -> {
                binding.btnMicrophone.visibility = View.GONE
                binding.layoutRecordingIndicator.visibility = View.VISIBLE
                binding.tvRecordingStatus.text = "Recording..."
                binding.btnStopRecording.visibility = View.VISIBLE
                binding.btnCancelRecording.visibility = View.VISIBLE
                binding.etPrompt.isEnabled = false
                binding.btnGenerate.isEnabled = false
                startRecordingPulse()
            }
            RecordingState.Processing -> {
                stopRecordingPulse()
                binding.layoutRecordingIndicator.visibility = View.VISIBLE
                binding.tvRecordingStatus.text = "Processing audio..."
                binding.btnStopRecording.visibility = View.GONE
                binding.btnCancelRecording.visibility = View.GONE
                binding.etPrompt.isEnabled = false
                binding.btnGenerate.isEnabled = false
            }
            is RecordingState.Error -> {
                stopRecordingPulse()
                Log.e(TAG, "UI received RecordingState.Error: ${state.message}")
                binding.layoutRecordingIndicator.visibility = View.GONE
                binding.btnMicrophone.visibility = View.VISIBLE
                binding.btnMicrophone.isEnabled = true
                binding.btnStopRecording.visibility = View.GONE
                binding.btnCancelRecording.visibility = View.GONE
                binding.etPrompt.isEnabled = true
                binding.btnGenerate.isEnabled = true
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSkeletonPulse()
        _binding = null
    }

    private fun populatePreview(task: GeneratedTask) {
        if (!task.transcript.isNullOrBlank()) {
            if (binding.cardUserSpeech.visibility != View.VISIBLE) {
                binding.cardUserSpeech.alpha = 0f
                binding.cardUserSpeech.translationY = 20f
                binding.cardUserSpeech.visibility = View.VISIBLE
                binding.cardUserSpeech.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            binding.tvUserSpeechText.text = task.transcript
        } else {
            binding.cardUserSpeech.visibility = View.GONE
        }

        binding.tvPreviewTitle.text       = task.title
        binding.tvPreviewDescription.text = task.description

        binding.chipPriority.apply {
            text = task.priority.replaceFirstChar { it.uppercase() }
            val (bg, fg) = priorityColors(task.priority)
            chipBackgroundColor = ColorStateList.valueOf(bg)
            setTextColor(fg)
        }

        if (task.category != null) {
            binding.chipCategory.apply {
                visibility = View.VISIBLE
                text = task.category.name
                chipBackgroundColor = ColorStateList.valueOf(requireContext().getColor(R.color.primary_light))
                setTextColor(requireContext().getColor(R.color.primary))
            }
        } else {
            binding.chipCategory.visibility = View.GONE
        }

        binding.tvDueDate.text = if (task.dueDate.isNotBlank()) task.dueDate else "No due date"
        buildChecklist(task.checklist)
    }

    private fun buildChecklist(items: List<ChecklistItem>) {
        binding.layoutChecklist.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        items.forEach { item ->
            val row = inflater.inflate(R.layout.item_checklist_preview, binding.layoutChecklist, false) as CheckBox
            row.text         = item.text
            row.isChecked    = item.isChecked
            row.setOnCheckedChangeListener { _, checked ->
                item.isChecked = checked
                if (checked) {
                    ViewInteractionAnimator.animateCheckboxToggle(row) {}
                }
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 2 }
            binding.layoutChecklist.addView(row, params)
        }
    }

    private fun setupPreviewButtons(task: GeneratedTask) {
        binding.btnPreviewCancel.setClickFeedback()
        binding.btnEdit.setClickFeedback()
        binding.btnSave.setClickFeedback()

        binding.btnPreviewCancel.setOnClickListener {
            viewModel.resetState()
        }

        binding.btnEdit.setOnClickListener {
            val formattedDesc = viewModel.buildFormattedDescription(task)
            val intent = Intent(requireContext(), AddEditTaskActivity::class.java).apply {
                putExtra("prefill_title", task.title)
                putExtra("prefill_description", formattedDesc)
            }
            startActivity(intent)
            dismiss()
        }

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

    private fun priorityColors(priority: String): Pair<Int, Int> {
        val ctx = requireContext()
        return when (priority.uppercase()) {
            "HIGH"   -> ctx.getColor(R.color.error_container) to ctx.getColor(R.color.error)
            "MEDIUM" -> ctx.getColor(R.color.warning_container) to ctx.getColor(R.color.warning)
            else     -> ctx.getColor(R.color.info_container) to ctx.getColor(R.color.info)
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        viewModel.resetState()
    }
}