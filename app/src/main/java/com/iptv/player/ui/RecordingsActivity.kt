package com.iptv.player.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.iptv.player.R
import com.iptv.player.adapter.ChannelAdapter
import com.iptv.player.databinding.ActivityRecordingsBinding
import com.iptv.player.model.Channel
import java.io.File

/** Lists locally-recorded streams (see RecordingController) so the user can play or delete them. */
class RecordingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingsBinding
    private lateinit var adapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.recordings_title)

        adapter = ChannelAdapter(
            isFavorite = { false },
            onClick = { openRecording(it) },
            onLongClick = { confirmDelete(it) }
        )
        binding.recordingsGrid.layoutManager = GridLayoutManager(this, 2)
        binding.recordingsGrid.adapter = adapter

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun recordingsDir(): File = File(getExternalFilesDir(null), "Recordings")

    private fun refresh() {
        val files = recordingsDir().listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        val recordingChannels = files.map { file ->
            Channel(
                id = "",
                name = file.nameWithoutExtension,
                logoUrl = null,
                group = "Recordings",
                streamUrl = "file://${file.absolutePath}"
            )
        }
        adapter.submit(recordingChannels)
        binding.emptyState.visibility = if (recordingChannels.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openRecording(channel: Channel) {
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_CHANNEL_LIST, ArrayList(listOf(channel)))
                putExtra(PlayerActivity.EXTRA_START_INDEX, 0)
            }
        )
    }

    private fun confirmDelete(channel: Channel) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_recording_title)
            .setMessage(channel.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                val path = channel.streamUrl.removePrefix("file://")
                if (File(path).delete()) {
                    Toast.makeText(this, R.string.recording_deleted, Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
