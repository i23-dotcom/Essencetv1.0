package com.iptv.player.parser

import com.iptv.player.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Parses standard M3U / M3U8 IPTV playlists, e.g.:
 *
 * #EXTM3U
 * #EXTINF:-1 tvg-id="bbc1" tvg-name="BBC One" tvg-logo="http://.../bbc1.png" group-title="UK",BBC One
 * http://example.com/live/bbc1.m3u8
 */
object M3uParser {

    private val attrRegex = Regex("""([a-zA-Z0-9\-]+)="([^"]*)"""")

    fun parse(input: InputStream): List<Channel> {
        val channels = mutableListOf<Channel>()
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            var pendingName = ""
            var pendingId = ""
            var pendingLogo: String? = null
            var pendingGroup = "Uncategorized"
            var pendingShift = 0

            reader.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachLine

                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        pendingId = attrs["tvg-id"] ?: ""
                        pendingLogo = attrs["tvg-logo"]
                        pendingGroup = attrs["group-title"]?.ifBlank { null } ?: "Uncategorized"
                        pendingShift = attrs["tvg-shift"]?.toIntOrNull() ?: 0
                        // Channel name is whatever follows the last comma on the line
                        val commaIdx = line.lastIndexOf(',')
                        val nameFromLine = if (commaIdx != -1) line.substring(commaIdx + 1).trim() else ""
                        pendingName = attrs["tvg-name"]?.ifBlank { null } ?: nameFromLine.ifBlank { "Unnamed Channel" }
                    }
                    line.startsWith("#") -> {
                        // Other directives (#EXTM3U, #EXTGRP, #EXTVLCOPT, etc.) - ignored
                    }
                    else -> {
                        // This is the stream URL line, closes out the pending #EXTINF block
                        channels.add(
                            Channel(
                                id = pendingId,
                                name = pendingName,
                                logoUrl = pendingLogo,
                                group = pendingGroup,
                                streamUrl = line,
                                epgShift = pendingShift
                            )
                        )
                        pendingName = ""
                        pendingId = ""
                        pendingLogo = null
                        pendingGroup = "Uncategorized"
                        pendingShift = 0
                    }
                }
            }
        }
        return channels
    }
}
