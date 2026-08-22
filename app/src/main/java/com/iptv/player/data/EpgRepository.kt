package com.iptv.player.data

import com.iptv.player.model.EpgProgram
import com.iptv.player.parser.XmlTvParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class EpgRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** channelId -> sorted list of programmes for that channel */
    private var byChannel: Map<String, List<EpgProgram>> = emptyMap()

    val isLoaded: Boolean get() = byChannel.isNotEmpty()

    /** Call from a background thread (blocking network + parse). */
    fun loadFromUrl(url: String) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("EPG server returned HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Empty EPG response")
            val raw = body.byteStream()
            val stream = if (url.endsWith(".gz")) GZIPInputStream(raw) else raw
            val programs = XmlTvParser.parse(stream)
            byChannel = programs.groupBy { it.channelId }
                .mapValues { (_, list) -> list.sortedBy { it.startMillis } }
        }
    }

    fun nowAndNext(channelId: String): Pair<EpgProgram?, EpgProgram?> {
        val list = byChannel[channelId] ?: return null to null
        val now = System.currentTimeMillis()
        val nowProgram = list.firstOrNull { it.isNowPlaying(now) }
        val nextProgram = list.firstOrNull { it.startMillis > now }
        return nowProgram to nextProgram
    }

    fun fullDay(channelId: String): List<EpgProgram> = byChannel[channelId] ?: emptyList()
}
