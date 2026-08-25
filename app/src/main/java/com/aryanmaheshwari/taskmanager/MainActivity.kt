package com.aryanmaheshwari.taskmanager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.aryanmaheshwari.taskmanager.databinding.ActivityMainBinding
import com.aryanmaheshwari.taskmanager.ui.activity.AddEditTaskActivity
import com.aryanmaheshwari.taskmanager.ui.activity.AiTaskGeneratorBottomSheet
import com.aryanmaheshwari.taskmanager.ui.adapter.TaskAdapter
import com.aryanmaheshwari.taskmanager.ui.viewmodel.TaskViewModel
import com.aryanmaheshwari.taskmanager.utils.AdManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import androidx.recyclerview.widget.ItemTouchHelper
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import android.graphics.drawable.ColorDrawable
import com.google.android.gms.ads.RequestConfiguration
import com.aryanmaheshwari.taskmanager.utils.setClickFeedback
import com.aryanmaheshwari.taskmanager.utils.setCardFeedback
import com.aryanmaheshwari.taskmanager.utils.NetworkMonitor

/**
 * MainActivity handles the task list and ad integration points.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var adapter: TaskAdapter
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check Onboarding status
        val sharedPref = getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        if (!sharedPref.getBoolean("finished", false)) {
            startActivity(Intent(this, com.aryanmaheshwari.taskmanager.ui.activity.OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Network Monitor
        networkMonitor = NetworkMonitor(this)
        networkMonitor.isOnline.observe(this) { isOnline ->
            if (isOnline) {
                binding.tvConnectivityStatus.visibility = View.GONE
            } else {
                binding.tvConnectivityStatus.visibility = View.VISIBLE
            }
        }

        // 1. Initialize AdMob and Load Ads
        val requestConfiguration = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(
                RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED
            )
            .setTagForUnderAgeOfConsent(
                RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
            )
            .setMaxAdContentRating(
                RequestConfiguration.MAX_AD_CONTENT_RATING_T
            )
            .build()

        MobileAds.setRequestConfiguration(requestConfiguration)

        MobileAds.initialize(this){
            loadAds()
        }

        // 2. Setup Task Adapter
        adapter = TaskAdapter(
            onEdit = { task ->
                val intent = Intent(this, AddEditTaskActivity::class.java)
                intent.putExtra("task", task)
                startActivity(intent)
            },
            onDelete = { task -> viewModel.delete(task) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                adapter.deleteAt(viewHolder.bindingAdapterPosition)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val density = resources.displayMetrics.density
                val marginHorizontal = (16 * density).toInt()
                val marginBottom = (12 * density).toInt()

                val bgColor = ContextCompat.getColor(this@MainActivity, R.color.error)
                val icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_delete)
                
                val verticalSpace = itemView.height - marginBottom
                val iconHeight = icon?.intrinsicHeight ?: 0
                val iconWidth = icon?.intrinsicWidth ?: 0
                val iconMargin = (verticalSpace - iconHeight) / 2

                val background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = 12 * density // matching card corners
                }

                if (dX > 0) {
                    val rightBounds = (itemView.left + marginHorizontal + dX.toInt()).coerceAtMost(itemView.right - marginHorizontal)
                    background.setBounds(
                        itemView.left + marginHorizontal,
                        itemView.top,
                        rightBounds,
                        itemView.bottom - marginBottom
                    )
                    icon?.setBounds(
                        itemView.left + marginHorizontal + iconMargin,
                        itemView.top + iconMargin,
                        itemView.left + marginHorizontal + iconMargin + iconWidth,
                        itemView.bottom - marginBottom - iconMargin
                    )
                } else if (dX < 0) {
                    val leftBounds = (itemView.right - marginHorizontal + dX.toInt()).coerceAtLeast(itemView.left + marginHorizontal)
                    background.setBounds(
                        leftBounds,
                        itemView.top,
                        itemView.right - marginHorizontal,
                        itemView.bottom - marginBottom
                    )
                    icon?.setBounds(
                        itemView.right - marginHorizontal - iconMargin - iconWidth,
                        itemView.top + iconMargin,
                        itemView.right - marginHorizontal - iconMargin,
                        itemView.bottom - marginBottom - iconMargin
                    )
                } else {
                    background.setBounds(0, 0, 0, 0)
                }

                if (dX != 0f) {
                    background.draw(c)
                    icon?.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerView)

        // 3. Observers
        viewModel.filteredTasks.observe(this) { tasks ->
            adapter.setTasks(tasks)
            binding.layoutEmptyState.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
            binding.layoutTaskHeader.visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
            binding.tvTaskCount.text = "${tasks.size} planned"
        }

        viewModel.allTasks.observe(this) {
            updatePremiumUI()
        }

        // 4. Command Dock - Add Task
        binding.btnDockAddTask.setOnClickListener {
            if (viewModel.canAddTask()) {
                startActivity(Intent(this, AddEditTaskActivity::class.java))
            } else {
                showPremiumExplanationDialog {
                    Toast.makeText(this, "Loading ad...", Toast.LENGTH_SHORT).show()
                    AdManager.showRewardedAd(
                        activity = this,
                        onUserEarnedReward = {
                            viewModel.setPremiumForToday()
                            updatePremiumUI()
                            Toast.makeText(this, "Premium workspace unlocked!", Toast.LENGTH_LONG).show()
                            startActivity(Intent(this, AddEditTaskActivity::class.java))
                        },
                        onAdDismissed = {
                            AdManager.loadRewardedAd(this)
                        }
                    )
                }
            }
        }

        // 5. Reward Ad Integration
        val triggerPremiumAd = View.OnClickListener {
            if (viewModel.isPremium) return@OnClickListener
            showPremiumExplanationDialog {
                Toast.makeText(this, "Loading ad...", Toast.LENGTH_SHORT).show()
                AdManager.showRewardedAd(
                    activity = this,
                    onUserEarnedReward = {
                        viewModel.setPremiumForToday()
                        updatePremiumUI()
                        Toast.makeText(this, "Premium workspace unlocked!", Toast.LENGTH_LONG).show()
                    },
                    onAdDismissed = {
                        AdManager.loadRewardedAd(this)
                    }
                )
            }
        }
        binding.premiumCard.setOnClickListener(triggerPremiumAd)
        binding.btnUnlockPremium.setOnClickListener(triggerPremiumAd)

        // 6. AI Architect Actions
        val openAiSheet = View.OnClickListener {
            AiTaskGeneratorBottomSheet().show(
                supportFragmentManager,
                AiTaskGeneratorBottomSheet.TAG
            )
        }
        binding.btnDockAiGenerator.setOnClickListener(openAiSheet)
        binding.btnAiHeroAction.setOnClickListener(openAiSheet)

        // Apply visual interaction animations
        binding.btnDockAddTask.setClickFeedback()
        binding.premiumCard.setCardFeedback()
        binding.btnUnlockPremium.setClickFeedback()
        binding.btnDockAiGenerator.setClickFeedback()
        binding.btnAiHeroAction.setClickFeedback()
        binding.aiHeroCard.setCardFeedback()

        // 6. Search Functionality & Focus animation
        binding.searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            val strokeColor = if (hasFocus) {
                ContextCompat.getColor(this, R.color.primary)
            } else {
                ContextCompat.getColor(this, R.color.divider)
            }
            binding.searchCard.strokeColor = strokeColor
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.checkAndResetInterstitialTrigger()) {
            AdManager.showInterstitialAd(this) {
                AdManager.loadInterstitialAd(this)
            }
        }
        updatePremiumUI()
    }

    private fun loadAds() {
        // ID and Size are set in XML via @string/banner_ad_unit_id and app:adSize
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)

        AdManager.loadInterstitialAd(this)
        AdManager.loadRewardedAd(this)
    }

    private fun showPremiumExplanationDialog(onConfirm: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_premium_explanation, null)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.Widget_App_Dialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val btnWatchAd = dialogView.findViewById<View>(R.id.btnWatchAd)
        val btnMaybeLater = dialogView.findViewById<View>(R.id.btnMaybeLater)

        btnWatchAd.setClickFeedback()
        btnMaybeLater.setClickFeedback()

        btnWatchAd.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        btnMaybeLater.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updatePremiumUI() {
        val totalTasksCount = viewModel.allTasks.value?.size ?: 0
        val maxLimit = 10
        if (viewModel.isPremium) {
            binding.premiumCard.visibility = View.GONE
            binding.tvProgressPercent.text = "Premium Workspace"
            binding.progressTasks.max = 100
            binding.progressTasks.progress = 100
            binding.progressTasks.progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.secondary)
            )
        } else {
            binding.premiumCard.visibility = View.VISIBLE
            if (!viewModel.canAddTask()) {
                binding.btnUnlockPremium.text = "👑 Limit Reached"
            } else {
                binding.btnUnlockPremium.text = "👑 Go Premium"
            }
            
            binding.tvProgressPercent.text = "$totalTasksCount / $maxLimit Tasks Used"
            binding.progressTasks.max = maxLimit
            binding.progressTasks.progress = totalTasksCount.coerceAtMost(maxLimit)
            
            val progressColor = if (totalTasksCount >= maxLimit) R.color.error else R.color.primary
            binding.progressTasks.progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, progressColor)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.unregister()
    }
}
