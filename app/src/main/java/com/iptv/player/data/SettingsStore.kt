package com.iptv.player.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)

    var playlistUrl: String?
        get() = prefs.getString(KEY_PLAYLIST_URL, null)
        set(value) = prefs.edit().putString(KEY_PLAYLIST_URL, value).apply()

    var playlistLocalUri: String?
        get() = prefs.getString(KEY_PLAYLIST_LOCAL_URI, null)
        set(value) = prefs.edit().putString(KEY_PLAYLIST_LOCAL_URI, value).apply()

    var epgUrl: String?
        get() = prefs.getString(KEY_EPG_URL, null)
        set(value) = prefs.edit().putString(KEY_EPG_URL, value).apply()

    var lastChannelKey: String?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_CHANNEL, value).apply()

    var parentalPin: String?
        get() = prefs.getString(KEY_PARENTAL_PIN, null)
        set(value) = prefs.edit().putString(KEY_PARENTAL_PIN, value).apply()

    /** Seconds of media buffered ahead of playback before/during a stream, to smooth over network jitter. */
    var bufferSeconds: Int
        get() = prefs.getInt(KEY_BUFFER_SECONDS, DEFAULT_BUFFER_SECONDS)
        set(value) = prefs.edit().putInt(KEY_BUFFER_SECONDS, value).apply()

    companion object {
        private const val KEY_PLAYLIST_URL = "playlist_url"
        private const val KEY_PLAYLIST_LOCAL_URI = "playlist_local_uri"
        private const val KEY_EPG_URL = "epg_url"
        private const val KEY_LAST_CHANNEL = "last_channel"
        private const val KEY_PARENTAL_PIN = "parental_pin"
        private const val KEY_BUFFER_SECONDS = "buffer_seconds"
        const val DEFAULT_BUFFER_SECONDS = 30
    }
}
