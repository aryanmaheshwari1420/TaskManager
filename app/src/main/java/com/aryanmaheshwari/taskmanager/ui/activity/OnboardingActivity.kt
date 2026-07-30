package com.aryanmaheshwari.taskmanager.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.aryanmaheshwari.taskmanager.MainActivity
import com.aryanmaheshwari.taskmanager.R
import com.aryanmaheshwari.taskmanager.databinding.ActivityOnboardingBinding
import com.aryanmaheshwari.taskmanager.databinding.ItemOnboardingPageBinding
import kotlin.math.abs

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOnboardingItems()
        setupIndicators()
        setCurrentIndicator(0)

        // Add Premium Page Transformer
        if (!areAnimationsDisabled()) {
            binding.viewPager.setPageTransformer(OnboardingPageTransformer())
        }
        binding.viewPager.setPageTransformer { page, position ->
            page.apply {
                alpha = 0.4f + (1 - kotlin.math.abs(position)) * 0.6f
                val scale = 0.92f + (1 - kotlin.math.abs(position)) * 0.08f
                scaleX = scale
                scaleY = scale
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                setCurrentIndicator(position)
                if (position == adapter.itemCount - 1) {
                    binding.btnNext.text = "Get Started"
                } else {
                    binding.btnNext.text = "Next"
                }
            }
        })

        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem + 1 < adapter.itemCount) {
                binding.viewPager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun setupOnboardingItems() {
        val items = listOf(
            OnboardingItem(
                R.drawable.ic_launcher_foreground,
                "Welcome to TaskManager",
                "The easiest way to organize your daily tasks and boost your productivity."
            ),
            OnboardingItem(
                R.drawable.ic_launcher_foreground,
                "Add Tasks Quickly",
                "Tap the plus button to add a new task with a title and description in seconds."
            ),
            OnboardingItem(
                R.drawable.ic_launcher_foreground,
                "Manage Your Tasks",
                "Edit tasks by tapping them or delete them easily to keep your list clean."
            )
        )
        adapter = OnboardingAdapter(items)
        binding.viewPager.adapter = adapter
    }

    private fun setupIndicators() {
        val indicators = arrayOfNulls<ImageView>(adapter.itemCount)
        val marginPx = (4 * resources.displayMetrics.density).toInt() // 4dp, density-correct
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(marginPx, 0, marginPx, 0)
        for (i in indicators.indices) {
            indicators[i] = ImageView(applicationContext)
            indicators[i]?.apply {
                setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.indicator_inactive))
                this.layoutParams = layoutParams
            }
            binding.layoutIndicators.addView(indicators[i])
        }
    }

    private fun setCurrentIndicator(index: Int) {
        val childCount = binding.layoutIndicators.childCount
        for (i in 0 until childCount) {
            val imageView = binding.layoutIndicators.getChildAt(i) as ImageView
            if (i == index) {
                imageView.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.indicator_active))
            } else {
                imageView.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.indicator_inactive))
            }
        }
    }

    private fun finishOnboarding() {
        val sharedPref = getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("finished", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun areAnimationsDisabled(): Boolean {
        val durationScale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        return durationScale == 0f
    }

    data class OnboardingItem(val image: Int, val title: String, val description: String)

    inner class OnboardingAdapter(private val items: List<OnboardingItem>) :
        RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
            return OnboardingViewHolder(
                ItemOnboardingPageBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class OnboardingViewHolder(private val binding: ItemOnboardingPageBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: OnboardingItem) {
                binding.ivOnboarding.setImageResource(item.image)
                binding.tvTitle.text = item.title
                binding.tvDescription.text = item.description
                
                // Subtle entrance animation for page content
                if (!areAnimationsDisabled()) {
                    binding.ivOnboarding.alpha = 0f
                    binding.ivOnboarding.scaleX = 0.8f
                    binding.ivOnboarding.scaleY = 0.8f
                    binding.ivOnboarding.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).start()
                }
            }
        }
    }

    /**
     * Subtle Fade + Scale Page Transformer for ViewPager2
     */
    class OnboardingPageTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            page.apply {
                val absPos = abs(position)
                alpha = 1f - absPos
                scaleY = 0.85f + (1f - absPos) * 0.15f
                scaleX = 0.85f + (1f - absPos) * 0.15f
                translationX = -position * width / 2
            }
        }
    }
}
