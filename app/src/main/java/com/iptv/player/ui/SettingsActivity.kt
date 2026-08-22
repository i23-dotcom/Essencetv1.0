package com.iptv.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.iptv.player.IptvApp
import com.iptv.player.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var app: IptvApp
    private var pickedLocalUri: Uri? = null

    companion object {
        const val EXTRA_FIRST_RUN = "extra_first_run"
        private const val LOW_BUFFER_SECONDS = 5
        private const val BALANCED_BUFFER_SECONDS = 15
        private const val SMOOTH_BUFFER_SECONDS = 30
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pickedLocalUri = uri
            binding.localFileLabel.text = uri.lastPathSegment ?: uri.toString()
            binding.playlistUrlInput.setText("") // URL and local file are mutually exclusive
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = IptvApp.from(this)

        binding.playlistUrlInput.setText(app.settings.playlistUrl.orEmpty())
        binding.epgUrlInput.setText(app.settings.epgUrl.orEmpty())
        app.settings.playlistLocalUri?.let { binding.localFileLabel.text = Uri.parse(it).lastPathSegment ?: it }

        binding.pickFileButton.setOnClickListener {
            filePicker.launch(arrayOf("*/*"))
        }

        setupBufferPresets()

        binding.saveButton.setOnClickListener { save() }
    }

    private fun setupBufferPresets() {
        val buttons = mapOf(
            LOW_BUFFER_SECONDS to binding.bufferLowButton,
            BALANCED_BUFFER_SECONDS to binding.bufferBalancedButton,
            SMOOTH_BUFFER_SECONDS to binding.bufferSmoothButton
        )
        fun refreshSelection(selected: Int) {
            buttons.forEach { (seconds, button) -> button.isSelected = seconds == selected }
        }
        refreshSelection(app.settings.bufferSeconds)
        buttons.forEach { (seconds, button) ->
            button.setOnClickListener {
                app.settings.bufferSeconds = seconds
                refreshSelection(seconds)
            }
        }
    }

    private fun save() {
        val url = binding.playlistUrlInput.text.toString().trim()
        val epg = binding.epgUrlInput.text.toString().trim()

        if (url.isBlank() && pickedLocalUri == null && app.settings.playlistLocalUri.isNullOrBlank()) {
            Toast.makeText(this, "Enter a playlist URL or choose a local M3U file", Toast.LENGTH_LONG).show()
            return
        }

        if (url.isNotBlank()) {
            app.settings.playlistUrl = url
            app.settings.playlistLocalUri = null
        } else if (pickedLocalUri != null) {
            app.settings.playlistLocalUri = pickedLocalUri.toString()
            app.settings.playlistUrl = null
        }
        app.settings.epgUrl = epg.ifBlank { null }

        val isFirstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
        if (isFirstRun) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        Toast.makeText(this, "Saved. Reloading playlist…", Toast.LENGTH_SHORT).show()
        finish()
    }
}
