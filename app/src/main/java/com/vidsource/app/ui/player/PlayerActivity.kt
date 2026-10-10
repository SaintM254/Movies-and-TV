package com.vidsource.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.vidsource.app.R
import com.vidsource.app.VidsourceApp
import com.vidsource.app.util.AppUtil

/**
 * Native player.
 *
 * Touch:  double-tap left/right half of the screen to seek -10s / +10s,
 *         single tap to show or hide controls.
 * Remote: D-pad left/right seeks (hold to keep seeking), D-pad center plays
 *         or pauses, D-pad up shows the controls, CH+/CH- switches source,
 *         MENU opens the source list. Rewind / Fast-forward keys seek too.
 */
@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var seekHint: TextView
    private lateinit var channelToast: TextView
    private lateinit var titleText: TextView
    private lateinit var sourceText: TextView

    private val repo get() = (application as VidsourceApp).sources

    private lateinit var sourceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        AppUtil.keepScreenOn(this)
        AppUtil.hideSystemUi(this)

        playerView = findViewById(R.id.playerView)
        seekHint = findViewById(R.id.seekHint)
        channelToast = findViewById(R.id.channelToast)
        titleText = findViewById(R.id.playerTitle)
        sourceText = findViewById(R.id.playerSourceName)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        sourceId = intent.getStringExtra(EXTRA_SOURCE_ID) ?: repo.getActive().id
        titleText.text = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        sourceText.text = intent.getStringExtra(EXTRA_SOURCE_NAME) ?: ""

        val faviconView = findViewById<ImageView>(R.id.playerSourceIcon)
        val sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL) ?: ""
        val favicons = (application as VidsourceApp).favicons
        faviconView.setImageBitmap(
            favicons.load(sourceUrl) ?: favicons.letterAvatar(
                intent.getStringExtra(EXTRA_SOURCE_NAME) ?: "S", sourceUrl
            )
        )

        buildPlayer(url)
        setupGestures()
    }

    private fun buildPlayer(url: String) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(HTTP_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)

        val referer = intent.getStringExtra(EXTRA_REFERER)
        if (!referer.isNullOrBlank()) {
            httpFactory.setDefaultRequestProperties(mapOf("Referer" to referer))
        }
        val ua = intent.getStringExtra(EXTRA_USER_AGENT)
        if (!ua.isNullOrBlank()) {
            httpFactory.setUserAgent(ua)
        }

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            // Rewind / fast-forward buttons and D-pad seek steps (TV remotes).
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
            .also { p ->
                p.setMediaItem(MediaItem.fromUri(url))
                p.playWhenReady = true
                p.addListener(playerListener)
                p.prepare()
            }
        player = exoPlayer
        playerView.player = exoPlayer
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                playerView.showController()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            seekHint.text = getString(R.string.playback_error, error.errorCodeName)
            seekHint.visibility = View.VISIBLE
        }
    }

    // ------------------------------------------------------------------
    // Double-tap / tap gestures
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        // Consuming every touch here disables PlayerView's own tap-to-toggle so
        // double-taps are never swallowed by the controls appearing mid-gesture.
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleController()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val forward = e.x > playerView.width / 2f
                seekBy(if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }
    }

    // ------------------------------------------------------------------
    // Seeking / playback
    // ------------------------------------------------------------------

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(target)
        showSeekHint(deltaMs)
    }

    private fun showSeekHint(deltaMs: Long) {
        val seconds = kotlin.math.abs(deltaMs) / 1000
        seekHint.text = getString(
            if (deltaMs < 0) R.string.seek_back else R.string.seek_forward,
            seconds
        )
        seekHint.visibility = View.VISIBLE
        seekHint.removeCallbacks(hideSeekHint)
        seekHint.postDelayed(hideSeekHint, SEEK_HINT_MS)
    }

    private val hideSeekHint = Runnable {
        seekHint.visibility = View.GONE
    }

    private fun togglePlayPause() {
        val p = player ?: return
        p.playWhenReady = !p.isPlaying
    }

    private fun toggleController() {
        if (playerView.isControllerFullyVisible) {
            playerView.hideController()
        } else {
            playerView.showController()
        }
    }

    // ------------------------------------------------------------------
    // Channel switching (source zap)
    // ------------------------------------------------------------------

    private fun zapSource(step: Int) {
        val next = repo.nextSource(sourceId, step)
        repo.setActive(next.id)
        showChannelToast(next.name)
        val data = Intent().putExtra(EXTRA_SOURCE_ID, next.id)
        setResult(RESULT_SWITCH_SOURCE, data)
        playerView.postDelayed({ finish() }, 450)
    }

    private fun showChannelToast(name: String) {
        channelToast.text = getString(R.string.switching_to, name)
        channelToast.visibility = View.VISIBLE
        channelToast.removeCallbacks(hideChannelToast)
        channelToast.postDelayed(hideChannelToast, 1500)
    }

    private val hideChannelToast = Runnable {
        channelToast.visibility = View.GONE
    }

    private fun showSourcePicker() {
        val sources = repo.getSources()
        val names = sources.map { it.name }.toTypedArray()
        val current = sources.indexOfFirst { it.id == sourceId }
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Vidsource_AlertDialog)
            .setTitle(R.string.choose_source)
            .setSingleChoiceItems(names, if (current >= 0) current else 0) { dialog, which ->
                dialog.dismiss()
                val target = sources[which]
                repo.setActive(target.id)
                setResult(RESULT_SWITCH_SOURCE, Intent().putExtra(EXTRA_SOURCE_ID, target.id))
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Remote / keyboard
    // ------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val p = player
        val controllerVisible = playerView.isControllerFullyVisible
        val repeat = event?.repeatCount ?: 0

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_J -> {
                if (controllerVisible && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    // Let focus move between controller buttons.
                    return super.onKeyDown(keyCode, event)
                }
                seekBy(-seekStepFor(repeat))
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_L -> {
                if (controllerVisible && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return super.onKeyDown(keyCode, event)
                }
                seekBy(seekStepFor(repeat))
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (controllerVisible && currentFocus != null &&
                    currentFocus !is PlayerView
                ) {
                    // A controller button has focus: let it handle the click.
                    return super.onKeyDown(keyCode, event)
                }
                togglePlayPause()
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                playerView.showController()
                playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playerView.hideController()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                p?.playWhenReady = true; return true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                p?.playWhenReady = false; return true
            }

            KeyEvent.KEYCODE_CHANNEL_UP -> {
                zapSource(-1); return true
            }

            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                zapSource(1); return true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                zapSource(1); return true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                zapSource(-1); return true
            }

            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE -> {
                showSourcePicker(); return true
            }

            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (playerView.isControllerFullyVisible) {
                    playerView.hideController()
                    return true
                }
                setResult(RESULT_CANCELED)
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Holding a seek key keeps seeking faster (remote key repeat). */
    private fun seekStepFor(repeat: Int): Long =
        if (repeat > 2) SEEK_STEP_MS * 3 else SEEK_STEP_MS

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        player?.removeListener(playerListener)
        player?.release()
        player = null
        playerView.player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_REFERER = "referer"
        const val EXTRA_USER_AGENT = "ua"
        const val EXTRA_SOURCE_NAME = "source_name"
        const val EXTRA_SOURCE_URL = "source_url"
        const val EXTRA_SOURCE_ID = "source_id"

        const val RESULT_SWITCH_SOURCE = 1001

        private const val SEEK_STEP_MS = 10_000L
        private const val SEEK_HINT_MS = 900L
        private const val HTTP_TIMEOUT_MS = 15_000

        fun createIntent(
            context: Context,
            url: String,
            title: String,
            referer: String?,
            sourceName: String,
            sourceUrl: String,
            sourceId: String
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_REFERER, referer)
            putExtra(EXTRA_SOURCE_NAME, sourceName)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
            putExtra(EXTRA_SOURCE_ID, sourceId)
        }
    }
}
