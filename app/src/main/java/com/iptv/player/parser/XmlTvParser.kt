package com.iptv.player.parser

import android.util.Xml
import com.iptv.player.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses XMLTV EPG feeds (<tv><programme channel="..." start="..." stop="..."><title/></programme></tv>).
 * Keeps parsing lightweight/streaming (XmlPullParser) since EPG files can be tens of MB.
 */
object XmlTvParser {

    // XMLTV date format: 20240115120000 +0000
    private val formats = listOf(
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    )

    private fun parseTime(raw: String): Long {
        for (f in formats) {
            try {
                return f.parse(raw)?.time ?: continue
            } catch (_: Exception) { /* try next format */ }
        }
        return 0L
    }

    fun parse(input: InputStream): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var eventType = parser.eventType
        var currentChannelId = ""
        var currentStart = 0L
        var currentStop = 0L
        var currentTitle = ""
        var currentDesc = ""
        var inProgramme = false
        var textBuffer = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            inProgramme = true
                            currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                            currentStart = parseTime(parser.getAttributeValue(null, "start") ?: "")
                            currentStop = parseTime(parser.getAttributeValue(null, "stop") ?: "")
                            currentTitle = ""
                            currentDesc = ""
                        }
                        "title", "desc" -> textBuffer = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> if (inProgramme) textBuffer.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "title" -> currentTitle = textBuffer.toString().trim()
                        "desc" -> currentDesc = textBuffer.toString().trim()
                        "programme" -> {
                            if (inProgramme && currentChannelId.isNotBlank() && currentStop > currentStart) {
                                programs.add(
                                    EpgProgram(
                                        channelId = currentChannelId,
                                        title = currentTitle.ifBlank { "No title" },
                                        description = currentDesc,
                                        startMillis = currentStart,
                                        stopMillis = currentStop
                                    )
                                )
                            }
                            inProgramme = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return programs
    }
}
