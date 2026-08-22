package com.iptv.player.model

/**
 * A single programme entry from an XMLTV (EPG) feed.
 * startMillis / stopMillis are epoch millis in UTC.
 */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startMillis: Long,
    val stopMillis: Long
) {
    fun isNowPlaying(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis in startMillis until stopMillis
}
