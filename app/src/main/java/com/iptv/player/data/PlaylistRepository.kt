package com.iptv.player.data

import android.content.Context
import android.net.Uri
import com.iptv.player.model.Channel
import com.iptv.player.parser.M3uParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlaylistRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** In-memory cache of the last-loaded playlist so screens don't re-parse on every rotation. */
    var cachedChannels: List<Channel> = emptyList()
        private set

    /** Call from a background thread (this does blocking I/O). */
    fun loadFromUrl(url: String): List<Channel> {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Server returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            val channels = M3uParser.parse(body.byteStream())
            cachedChannels = channels
            return channels
        }
    }

    /** Call from a background thread. Loads a playlist file the user picked via SAF. */
    fun loadFromLocalUri(context: Context, uri: Uri): List<Channel> {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open selected file")
        stream.use {
            val channels = M3uParser.parse(it)
            cachedChannels = channels
            return channels
        }
    }

    /**
     * Call from a background thread. Loads the playlist bundled with the app
     * (app/src/main/assets/default_channels.m3u) so there's a working channel
     * list out of the box with no setup required.
     */
    fun loadDefaultChannels(context: Context): List<Channel> {
        context.assets.open(DEFAULT_ASSET_NAME).use { stream ->
            val channels = M3uParser.parse(stream)
            cachedChannels = channels
            return channels
        }
    }

    companion object {
        const val DEFAULT_ASSET_NAME = "default_channels.m3u"
    }

    fun groupedByCategory(channels: List<Channel> = cachedChannels): Map<String, List<Channel>> =
        channels.groupBy { it.group }.toSortedMap(compareBy { it.lowercase() })
}
