package com.iptv.player.record

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/**
 * Wraps an upstream [DataSource] and, while [controller] is actively recording,
 * duplicates the bytes of media-segment responses (.ts/.m4s/.mp4/.aac chunks -
 * not the small .m3u8/.mpd manifest text) into [controller]'s output file.
 *
 * Concatenated MPEG-TS segments like this play back fine afterwards in the
 * same player (or in VLC/most players) - it's a raw dump of exactly what was
 * streamed, in the order it was requested.
 */
@UnstableApi
class RecordingDataSource(
    private val upstream: DataSource,
    private val controller: RecordingController
) : DataSource by upstream {

    private var recordingThisOpen = false

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        recordingThisOpen = controller.isRecording && looksLikeMediaSegment(dataSpec.uri)
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(buffer, offset, length)
        if (bytesRead > 0 && recordingThisOpen) {
            controller.write(buffer, offset, bytesRead)
        }
        return bytesRead
    }

    override fun close() {
        recordingThisOpen = false
        upstream.close()
    }

    private fun looksLikeMediaSegment(uri: Uri): Boolean {
        val path = uri.path?.lowercase() ?: return false
        return SEGMENT_EXTENSIONS.any { path.endsWith(it) }
    }

    companion object {
        private val SEGMENT_EXTENSIONS = listOf(".ts", ".m4s", ".mp4", ".aac", ".m2ts")
    }
}

/** [DataSource.Factory] that produces [RecordingDataSource]s wrapping [upstreamFactory]'s sources. */
@UnstableApi
class RecordingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val controller: RecordingController
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        RecordingDataSource(upstreamFactory.createDataSource(), controller)
}
