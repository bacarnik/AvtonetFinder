package com.example.avtonetfinder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.avtonetfinder.databinding.ItemSearchBinding
import java.text.SimpleDateFormat
import java.util.*

class SearchAdapter(
    private var searches: List<Search>,
    private val onToggle: (Int, Boolean) -> Unit,
    private val onEdit: (Search) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val search = searches[position]
        holder.binding.apply {
            textName.text = search.name
            textUrl.text = search.url
            switchEnabled.isChecked = search.isEnabled
            
            val dateStr = if (search.lastChecked > 0) {
                SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(search.lastChecked))
            } else "Never"
            textLastChecked.text = "Last check: $dateStr"

            val progress = ScanManager.scanProgress.value[search.id]
            if (progress != null && progress.status != ScanStatus.IDLE) {
                val isTerminal = progress.status == ScanStatus.COMPLETED || progress.status == ScanStatus.FAILED || progress.status == ScanStatus.TIMEOUT
                if (isTerminal) {
                    textStatus.text = progress.message
                } else {
                    textStatus.text = "${progress.message} (${progress.currentStep}/${progress.totalSteps})"
                }
                textStatus.visibility = android.view.View.VISIBLE
            } else if (!search.lastResult.isNullOrEmpty()) {
                textStatus.text = search.lastResult
                textStatus.visibility = android.view.View.VISIBLE
            } else {
                textStatus.visibility = android.view.View.GONE
            }

            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(search.id, isChecked)
            }
            btnEdit.setOnClickListener { onEdit(search) }
            btnDelete.setOnClickListener { onDelete(search.id) }
        }
    }

    override fun getItemCount() = searches.size

    fun updateData(newSearches: List<Search>) {
        searches = newSearches
        notifyDataSetChanged()
    }
}
