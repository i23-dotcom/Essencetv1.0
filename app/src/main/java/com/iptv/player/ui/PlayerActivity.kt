package com.iptv.player.ui

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.load
import com.iptv.player.IptvApp
import com.iptv.player.databinding.ActivityPlayerBinding
import com.iptv.player.model.Channel
import com.iptv.player.record.RecordingController
import com.iptv.player.record.RecordingDataSourceFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var app: IptvApp
    private lateinit var recordingController: RecordingController
    private var player: ExoPlayer? = null

    private var channels: List<Channel> = emptyList()
    private var index: Int = 0
    private var resizeModeIndex = 0

    private val hideOverlayRunnable = Runnable { binding.overlay.visibility = View.GONE }
    private val recordingTimerHandler = Handler(Looper.getMainLooper())
    private var recordingStartMs = 0L
    private val recordingTimerRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - recordingStartMs
            val mins = TimeUnit.MILLISECONDS.toMinutes(elapsed)
            val secs = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
            binding.recordingLabel.text = String.format("● REC %02d:%02d", mins, secs)
            recordingTimerHandler.postDelayed(this, 1000L)
        }
    }

    // Seek bar (only relevant for local recordings, which are seekable VOD - live streams aren't)
    private val seekUpdateHandler = Handler(Looper.getMainLooper())
    private var isUserScrubbing = false
    private val seekUpdateRunnable = object : Runnable {
        override fun run() {
            updateSeekUi()
            seekUpdateHandler.postDelayed(this, 500L)
        }
    }

    companion object {
        const val EXTRA_CHANNEL_LIST = "extra_channel_list"
        const val EXTRA_START_INDEX = "extra_start_index"
        private const val OVERLAY_TIMEOUT_MS = 4000L
        private const val SKIP_MS = 10_000L
    }

    /** Recordings are played back from file:// URIs; everything else is a live stream. */
    private fun isCurrentRecording(): Boolean =
        channels.getOrNull(index)?.streamUrl?.startsWith("file://") == true

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = IptvApp.from(this)
        recordingController = RecordingController(this)

        @Suppress("UNCHECKED_CAST")
        channels = (intent.getSerializableExtra(EXTRA_CHANNEL_LIST) as? ArrayList<Channel>) ?: arrayListOf()
        index = intent.getIntExtra(EXTRA_START_INDEX, 0).coerceIn(0, (channels.size - 1).coerceAtLeast(0))

        binding.root.setOnClickListener { toggleOverlay() }
        binding.favoriteButton.setOnClickListener { toggleFavoriteCurrent() }
        binding.aspectButton.setOnClickListener { cycleResizeMode() }
        binding.recordButton.setOnClickListener { toggleRecording() }
        binding.nextButton.setOnClickListener { switchChannel(+1) }
        binding.prevButton.setOnClickListener { switchChannel(-1) }
        binding.pipButton.setOnClickListener { enterPipIfSupported() }

        binding.skipBackButton.setOnClickListener { skip(-SKIP_MS) }
        binding.skipForwardButton.setOnClickListener { skip(SKIP_MS) }
        binding.playPauseButton.setOnClickListener { togglePlayPause() }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.positionLabel.text = formatMs(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isUserScrubbing = true
                binding.overlay.removeCallbacks(hideOverlayRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isUserScrubbing = false
                player?.seekTo(seekBar.progress.toLong())
                showOverlayThenHide()
            }
        })

        initPlayer()
        playCurrent()
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        // Recording: bytes for media-segment requests get tee'd to disk while recordingController.isRecording.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val recordingHttpFactory = RecordingDataSourceFactory(httpDataSourceFactory, recordingController)
        // DefaultDataSource routes http(s) through the (recording-aware) factory above, and
        // file:// URIs (used to play back local recordings) straight to disk - no re-recording loop.
        val dataSourceFactory = DefaultDataSource.Factory(this, recordingHttpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Lag-reduction buffering: hold back playback start/resume until this many seconds of
        // media are buffered, so brief network hiccups don't cause stutter/rebuffering later.
        val bufferMs = app.settings.bufferSeconds.coerceIn(2, 60) * 1000
        val maxBufferMs = (bufferMs * 2).coerceAtLeast(bufferMs + 15_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(bufferMs, maxBufferMs, bufferMs, bufferMs)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
        binding.playerView.player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                binding.bufferingSpinner.visibility =
                    if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Playback error: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        player = exoPlayer
    }

    private fun playCurrent() {
        stopRecordingIfActive(showToast = false)

        val channel = channels.getOrNull(index) ?: return
        binding.channelNameLabel.text = channel.name
        binding.channelLogo.load(channel.logoUrl)
        binding.favoriteButton.setImageResource(
            if (app.favorites.isFavorite(channel.key))
                com.iptv.player.R.drawable.ic_favorite_filled
            else
                com.iptv.player.R.drawable.ic_favorite_border
        )
        app.favorites.addRecent(channel.key)
        app.settings.lastChannelKey = channel.key

        player?.apply {
            setMediaItem(MediaItem.fromUri(channel.streamUrl))
            prepare()
            playWhenReady = true
        }
        updateEpgLabel(channel)
        updateSeekControlsVisibility()
        showOverlayThenHide()
    }

    // --- Seek bar (recordings only) --------------------------------------------------------

    private fun updateSeekControlsVisibility() {
        val show = isCurrentRecording()
        binding.seekControlsRow.visibility = if (show) View.VISIBLE else View.GONE
        binding.seekBarRow.visibility = if (show) View.VISIBLE else View.GONE
        // Live channel-switch chevrons only make sense when browsing live TV, not a single recording.
        binding.prevButton.visibility = if (show) View.GONE else View.VISIBLE
        binding.nextButton.visibility = if (show) View.GONE else View.VISIBLE
        seekUpdateHandler.removeCallbacks(seekUpdateRunnable)
        if (show) seekUpdateHandler.post(seekUpdateRunnable)
    }

    private fun updateSeekUi() {
        val p = player ?: return
        if (!isCurrentRecording()) return
        val duration = p.duration.coerceAtLeast(0L)
        binding.seekBar.max = duration.toInt().coerceAtLeast(1)
        if (!isUserScrubbing) {
            binding.seekBar.progress = p.currentPosition.toInt().coerceIn(0, binding.seekBar.max)
            binding.positionLabel.text = formatMs(p.currentPosition)
        }
        binding.durationLabel.text = formatMs(duration)
        binding.playPauseButton.setImageResource(
            if (p.isPlaying) com.iptv.player.R.drawable.ic_pause else com.iptv.player.R.drawable.ic_play_arrow
        )
    }

    private fun togglePlayPause() {
        val p = player ?: return
        p.playWhenReady = !p.playWhenReady
        binding.playPauseButton.setImageResource(
            if (p.playWhenReady) com.iptv.player.R.drawable.ic_pause else com.iptv.player.R.drawable.ic_play_arrow
        )
        showOverlayThenHide()
    }

    private fun skip(deltaMs: Long) {
        val p = player ?: return
        val target = (p.currentPosition + deltaMs).coerceIn(0L, p.duration.coerceAtLeast(0L))
        p.seekTo(target)
        showOverlayThenHide()
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun updateEpgLabel(channel: Channel) {
        if (channel.id.isBlank() || !app.epgRepository.isLoaded) {
            binding.epgLabel.visibility = View.GONE
            return
        }
        val (now, next) = app.epgRepository.nowAndNext(channel.id)
        if (now == null) {
            binding.epgLabel.visibility = View.GONE
            return
        }
        binding.epgLabel.visibility = View.VISIBLE
        val nextText = next?.let { " · Next: ${it.title}" } ?: ""
        binding.epgLabel.text = "Now: ${now.title}$nextText"
    }

    private fun switchChannel(delta: Int) {
        if (channels.isEmpty()) return
        index = (index + delta + channels.size) % channels.size
        playCurrent()
    }

    private fun toggleFavoriteCurrent() {
        val channel = channels.getOrNull(index) ?: return
        val nowFav = app.favorites.toggleFavorite(channel.key)
        binding.favoriteButton.setImageResource(
            if (nowFav) com.iptv.player.R.drawable.ic_favorite_filled else com.iptv.player.R.drawable.ic_favorite_border
        )
    }

    // --- Recording -----------------------------------------------------------------------

    private fun toggleRecording() {
        if (recordingController.isRecording) {
            stopRecordingIfActive(showToast = true)
        } else {
            val channel = channels.getOrNull(index) ?: return
            recordingController.start(channel.name)
            binding.recordButton.setImageResource(com.iptv.player.R.drawable.ic_record_active)
            binding.recordingLabel.visibility = View.VISIBLE
            recordingStartMs = System.currentTimeMillis()
            recordingTimerHandler.post(recordingTimerRunnable)
            Toast.makeText(this, com.iptv.player.R.string.recording_started, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecordingIfActive(showToast: Boolean) {
        if (!recordingController.isRecording) return
        val file = recordingController.stop()
        recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
        binding.recordButton.setImageResource(com.iptv.player.R.drawable.ic_record)
        binding.recordingLabel.visibility = View.GONE
        if (showToast && file != null) {
            Toast.makeText(this, getString(com.iptv.player.R.string.recording_saved, file.name), Toast.LENGTH_LONG).show()
        }
    }

    // --- Aspect ratio / PiP / overlay -----------------------------------------------------

    private val resizeModes = intArrayOf(
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    )

    private fun cycleResizeMode() {
        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
        binding.playerView.resizeMode = resizeModes[resizeModeIndex]
    }

    private fun toggleOverlay() {
        binding.overlay.removeCallbacks(hideOverlayRunnable)
        if (binding.overlay.visibility == View.VISIBLE) {
            binding.overlay.visibility = View.GONE
        } else {
            showOverlayThenHide()
        }
    }

    private fun showOverlayThenHide() {
        binding.overlay.visibility = View.VISIBLE
        binding.overlay.removeCallbacks(hideOverlayRunnable)
        binding.overlay.postDelayed(hideOverlayRunnable, OVERLAY_TIMEOUT_MS)
    }

    private fun enterPipIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "Picture-in-picture isn't supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // Remote-control friendly: DPAD/volume-style channel-up/down and back-to-select behavior.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> { switchChannel(+1); true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> { switchChannel(-1); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { switchChannel(+1); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { switchChannel(-1); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { toggleOverlay(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStop() {
        super.onStop()
        stopRecordingIfActive(showToast = false)
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecordingIfActive(showToast = false)
        recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
        seekUpdateHandler.removeCallbacks(seekUpdateRunnable)
        player?.release()
        player = null
    }
}
