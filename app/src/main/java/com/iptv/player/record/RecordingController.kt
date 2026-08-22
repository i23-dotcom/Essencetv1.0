package com.iptv.player.record

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns the currently-open recording file, if any. One instance lives for the
 * lifetime of the player screen. [RecordingDataSource] asks it whether to
 * write bytes, and hands it the bytes to persist.
 *
 * Files are written to this app's own external-files directory
 * (`.../Android/data/com.iptv.player/files/Recordings`), which needs no
 * storage permission on modern Android and isn't visible to other apps.
 */
class RecordingController(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    var isRecording = false
        private set

    private var outputStream: OutputStream? = null

    @Volatile
    var currentFile: File? = null
        private set

    /** Starts a new recording for [channelName], closing any previous one first. Returns the output file. */
    @Synchronized
    fun start(channelName: String): File {
        stop()
        val dir = File(appContext.getExternalFilesDir(null), "Recordings").apply { mkdirs() }
        val safeName = channelName.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "channel" }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "${safeName}_$timestamp.ts")
        outputStream = BufferedOutputStream(FileOutputStream(file))
        currentFile = file
        isRecording = true
        return file
    }

    /** Called from the player's network-loading thread; keep this fast and non-blocking-ish. */
    @Synchronized
    fun write(buffer: ByteArray, offset: Int, length: Int) {
        try {
            outputStream?.write(buffer, offset, length)
        } catch (_: Exception) {
            // Disk full, permission revoked mid-write, etc. Stop cleanly rather than crash playback.
            stop()
        }
    }

    /** Stops recording (if active) and returns the finished file, or null if nothing was recording. */
    @Synchronized
    fun stop(): File? {
        val finished = if (isRecording) currentFile else null
        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (_: Exception) {
            // Best-effort close.
        }
        outputStream = null
        currentFile = null
        isRecording = false
        return finished
    }
}
