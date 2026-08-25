package com.aryanmaheshwari.taskmanager.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aryanmaheshwari.taskmanager.R
import com.aryanmaheshwari.taskmanager.data.local.AiSnapshot
import com.aryanmaheshwari.taskmanager.databinding.ItemAiSnapshotBinding
import com.aryanmaheshwari.taskmanager.utils.setCardFeedback
import com.aryanmaheshwari.taskmanager.utils.setIconButtonFeedback

class AiSnapshotAdapter(
    private val onClick: (AiSnapshot) -> Unit,
    private val onDelete: (AiSnapshot) -> Unit
) : RecyclerView.Adapter<AiSnapshotAdapter.ViewHolder>() {

    private var items = emptyList<AiSnapshot>()

    fun setSnapshots(newList: List<AiSnapshot>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAiSnapshotBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemAiSnapshotBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AiSnapshot) {
            binding.tvTitle.text = item.title

            val ctx = binding.root.context
            if (item.isPending) {
                binding.tvSubtitle.text = "Offline Draft • Tap to generate when online"
                binding.tvSubtitle.setTextColor(ContextCompat.getColor(ctx, R.color.warning))
                binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_check_circle)) // indicator of pending
                binding.ivIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.warning))
            } else {
                binding.tvSubtitle.text = "Generated Plan • Tap to reopen preview"
                binding.tvSubtitle.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.ic_menu_agenda))
                binding.ivIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.primary))
            }

            if (item.isVoice) {
                binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.ic_btn_speak_now))
            }

            // Bind click feedback animations
            binding.cardRoot.setCardFeedback()
            binding.btnDelete.setIconButtonFeedback()

            binding.cardRoot.setOnClickListener { onClick(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
