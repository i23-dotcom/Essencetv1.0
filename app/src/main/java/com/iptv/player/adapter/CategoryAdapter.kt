package com.iptv.player.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.iptv.player.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val categories: List<String>,
    private val onSelected: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var selectedIndex = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(position)
    override fun getItemCount() = categories.size

    inner class VH(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int) {
            binding.categoryLabel.text = categories[position]
            binding.root.isSelected = position == selectedIndex
            binding.root.setOnClickListener {
                val prev = selectedIndex
                selectedIndex = position
                notifyItemChanged(prev)
                notifyItemChanged(selectedIndex)
                onSelected(categories[position])
            }
        }
    }
}
