package com.iptv.player.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iptv.player.IptvApp
import com.iptv.player.databinding.ActivityEpgBinding
import com.iptv.player.databinding.ItemEpgRowBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows "Now / Next" for every channel that has an EPG id, i.e. a simple program guide. */
class EpgActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEpgBinding
    private lateinit var app: IptvApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpgBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = IptvApp.from(this)

        binding.epgRecycler.layoutManager = LinearLayoutManager(this)

        val epgUrl = app.settings.epgUrl
        if (epgUrl.isNullOrBlank()) {
            Toast.makeText(this, "Add an EPG (XMLTV) URL in Settings to see the guide", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.progress.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                if (!app.epgRepository.isLoaded) {
                    withContext(Dispatchers.IO) { app.epgRepository.loadFromUrl(epgUrl) }
                }
                populate()
            } catch (e: Exception) {
                Toast.makeText(this@EpgActivity, "Failed to load EPG: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progress.visibility = android.view.View.GONE
            }
        }
    }

    private fun populate() {
        val channels = app.playlistRepository.cachedChannels.filter { it.id.isNotBlank() }
        val rows = channels.mapNotNull { ch ->
            val (now, next) = app.epgRepository.nowAndNext(ch.id)
            if (now == null) null else Triple(ch.name, now.title, next?.title.orEmpty())
        }
        binding.epgRecycler.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<RowVH>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RowVH {
                val b = ItemEpgRowBinding.inflate(layoutInflater, parent, false)
                return RowVH(b)
            }
            override fun onBindViewHolder(holder: RowVH, position: Int) = holder.bind(rows[position])
            override fun getItemCount() = rows.size
        }
        binding.emptyState.visibility = if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private class RowVH(private val binding: ItemEpgRowBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        fun bind(row: Triple<String, String, String>) {
            binding.channelName.text = row.first
            binding.nowProgram.text = "Now: ${row.second}"
            binding.nextProgram.text = if (row.third.isNotBlank()) "Next: ${row.third}" else ""
        }
    }
}
