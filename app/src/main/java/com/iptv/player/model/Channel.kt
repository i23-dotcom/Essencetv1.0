package com.iptv.player.model

import java.io.Serializable

/**
 * One entry parsed from an M3U / M3U8 playlist (#EXTINF line + URL line).
 */
data class Channel(
    val id: String,          // tvg-id, used to match EPG programmes. Falls back to name.
    val name: String,        // tvg-name or the text after the last comma on #EXTINF
    val logoUrl: String?,    // tvg-logo
    val group: String,       // group-title, "Uncategorized" if absent
    val streamUrl: String,   // the actual media URL
    val epgShift: Int = 0    // tvg-shift, hours offset for EPG times, rarely used
) : Serializable {

    /** Stable key used for favorites / recent-channel storage. */
    val key: String get() = if (id.isNotBlank()) id else streamUrl
}
