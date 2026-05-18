package com.example.avtonetfinder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.avtonetfinder.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private var history: List<NotificationHistory>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = history[position]
        holder.binding.apply {
            textTitle.text = item.title
            val dateStr = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(item.timestamp))
            textTimestamp.text = dateStr
            root.setOnClickListener { onClick(item.url) }
        }
    }

    override fun getItemCount() = history.size

    fun updateData(newHistory: List<NotificationHistory>) {
        history = newHistory
        notifyDataSetChanged()
    }
}
