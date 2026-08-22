package com.iptv.player.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.iptv.player.IptvApp

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = IptvApp.from(this)
        val hasPlaylist = !app.settings.playlistUrl.isNullOrBlank() || !app.settings.playlistLocalUri.isNullOrBlank()

        val target = if (hasPlaylist) MainActivity::class.java else SettingsActivity::class.java
        startActivity(Intent(this, target).apply {
            if (!hasPlaylist) putExtra(SettingsActivity.EXTRA_FIRST_RUN, true)
        })
        finish()
    }
}
