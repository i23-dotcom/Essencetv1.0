package com.iptv.player.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.iptv.player.R
import com.iptv.player.databinding.ItemChannelBinding
import com.iptv.player.model.Channel

class ChannelAdapter(
    private val isFavorite: (Channel) -> Boolean,
    private val onClick: (Channel) -> Unit,
    private val onLongClick: (Channel) -> Unit,
    /** Optional stable channel number (e.g. its position in the full playlist). Null hides the badge. */
    private val numberFor: ((Channel) -> Int?)? = null
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private val items = mutableListOf<Channel>()

    fun submit(newItems: List<Channel>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos].key == newItems[newPos].key
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos] == newItems[newPos]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(channel: Channel) {
            binding.channelName.text = channel.name
            binding.channelLogo.load(channel.logoUrl) {
                placeholder(R.drawable.ic_channel_placeholder)
                error(R.drawable.ic_channel_placeholder)
            }
            binding.favoriteBadge.visibility =
                if (isFavorite(channel)) android.view.View.VISIBLE else android.view.View.GONE

            val number = numberFor?.invoke(channel)
            if (number != null) {
                binding.channelNumber.text = number.toString().padStart(3, '0')
                binding.channelNumber.visibility = android.view.View.VISIBLE
            } else {
                binding.channelNumber.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onClick(channel) }
            binding.root.setOnLongClickListener { onLongClick(channel); true }
            // Remote/D-pad focus scaling so it reads clearly as a "set-top box" UI
            binding.root.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.08f else 1f).scaleY(if (hasFocus) 1.08f else 1f).setDuration(120).start()
                binding.card.cardElevation = if (hasFocus) 16f else 4f
            }
        }
    }
}
