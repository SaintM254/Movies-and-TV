package com.vidsource.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vidsource.app.R
import com.vidsource.app.VidsourceApp
import com.vidsource.app.data.SourceProfile
import com.vidsource.app.ui.browser.DpadPointerView
import com.vidsource.app.ui.player.PlayerActivity
import com.vidsource.app.ui.settings.SettingsActivity
import com.vidsource.app.util.AppUtil
import kotlinx.coroutines.launch

/**
 * Browser screen: renders the active source website and hands detected
 * video URLs over to the native player. Sources are switched from the
 * favicon strip like TV channels (CH+/CH- on the remote).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var pointer: DpadPointerView
    private lateinit var sourceStrip: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var chrome: View
    private lateinit var hintBar: TextView
    private lateinit var sourceAdapter: SourceSwitcherAdapter

    private val repo get() = (application as VidsourceApp).sources
    private val faviconStore get() = (application as VidsourceApp).favicons

    private lateinit var currentSource: SourceProfile
    private var isTv = false
    private var hooksJs = ""
    private var lastDetectedUrl: String? = null
    private var lastDetectedAt = 0L
    private var playerDismissedAt = 0L
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val playerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            playerDismissedAt = System.currentTimeMillis()
            if (result.resultCode == PlayerActivity.RESULT_SWITCH_SOURCE) {
                result.data?.getStringExtra(PlayerActivity.EXTRA_SOURCE_ID)?.let { id ->
                    switchToSource(id)
                }
            }
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val sources = repo.getSources()
            val active = repo.getActive()
            if (active.id != currentSource.id) {
                sourceAdapter.submit(sources, active.id)
                switchToSource(active.id)
            } else {
                currentSource = active
                sourceAdapter.submit(sources, active.id)
                preloadMissingFavicons(sources)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        isTv = AppUtil.isTv(this)
        AppUtil.keepScreenOn(this)

        chrome = findViewById(R.id.chrome)
        titleView = findViewById(R.id.titleView)
        progressBar = findViewById(R.id.webProgress)
        sourceStrip = findViewById(R.id.sourceStrip)
        webView = findViewById(R.id.webView)
        pointer = findViewById(R.id.pointer)
        hintBar = findViewById(R.id.hintBar)

        hooksJs = readAsset("video_hooks.js")
        currentSource = repo.getActive()

        setupToolbar()
        setupSourceStrip()
        setupWebView()
        setupHints()

        val forcedUrl = intent?.getStringExtra(EXTRA_FORCE_SOURCE_URL)
        if (forcedUrl != null) {
            webView.loadUrl(SourceProfile.normalizeUrl(forcedUrl))
        } else {
            switchToSource(currentSource.id, reload = false)
            webView.loadUrl(currentSource.url)
        }
    }

    private fun setupToolbar() {
        titleView.text = currentSource.name
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener { openSettings() }
    }

    private fun setupSourceStrip() {
        sourceAdapter = SourceSwitcherAdapter(faviconStore) { source -> switchToSource(source.id) }
        sourceStrip.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        sourceStrip.adapter = sourceAdapter
        sourceAdapter.submit(repo.getSources(), currentSource.id)
        preloadMissingFavicons(repo.getSources())
    }

    private fun preloadMissingFavicons(sources: List<SourceProfile>) {
        sources.forEach { source ->
            if (faviconStore.load(source.url) == null) {
                lifecycleScope.launch {
                    faviconStore.fetchRemoteIcon(source.url)
                    sourceAdapter.refreshIcons()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = "$userAgentString Vidsource/1.0"
        }
        webView.addJavascriptInterface(VideoBridge(), "Vidsource")
        webView.webViewClient = BrowserClient()
        webView.webChromeClient = ChromeClient()

        if (isTv) {
            pointer.visibility = View.VISIBLE
            pointer.attach(webView, AppUtil.dp(this, 36f))
            webView.setOnFocusChangeListener { _, hasFocus ->
                pointer.pointerActive = hasFocus
                if (hasFocus) pointer.center()
            }
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true
            webView.requestFocus()
        } else {
            pointer.visibility = View.GONE
        }
    }

    private fun setupHints() {
        if (isTv) {
            hintBar.text = getString(R.string.hint_tv)
        } else {
            hintBar.text = getString(R.string.hint_mobile)
        }
    }

    // ------------------------------------------------------------------
    // Source switching (channel behaviour)
    // ------------------------------------------------------------------

    private fun switchToSource(id: String, reload: Boolean = true) {
        val target = repo.getSources().firstOrNull { it.id == id } ?: return
        repo.setActive(target.id)
        currentSource = target
        titleView.text = target.name
        sourceAdapter.setActiveId(target.id)
        if (reload) {
            webView.stopLoading()
            webView.loadUrl(target.url)
            if (isTv) {
                webView.requestFocus()
                pointer.center()
            }
        }
    }

    private fun zapSource(step: Int) {
        val next = repo.nextSource(currentSource.id, step)
        Toast.makeText(
            this,
            getString(R.string.switching_to, next.name),
            Toast.LENGTH_SHORT
        ).show()
        switchToSource(next.id)
    }

    // ------------------------------------------------------------------
    // Video detection -> native player
    // ------------------------------------------------------------------

    private fun handleDetectedVideo(url: String, title: String) {
        if (!AppUtil.isDirectMediaUrl(url)) return
        val now = System.currentTimeMillis()
        if (url == lastDetectedUrl && now - lastDetectedAt < DETECT_DEBOUNCE_MS) return
        if (now - playerDismissedAt < PLAYER_COOLDOWN_MS) return
        lastDetectedUrl = url
        lastDetectedAt = now
        openPlayer(url, title)
    }

    private fun openPlayer(url: String, title: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL, url)
            putExtra(PlayerActivity.EXTRA_TITLE, title.ifBlank { currentSource.name })
            putExtra(PlayerActivity.EXTRA_REFERER, webView.url ?: currentSource.url)
            putExtra(PlayerActivity.EXTRA_SOURCE_NAME, currentSource.name)
            putExtra(PlayerActivity.EXTRA_SOURCE_URL, currentSource.url)
            putExtra(PlayerActivity.EXTRA_SOURCE_ID, currentSource.id)
        }
        playerLauncher.launch(intent)
    }

    // ------------------------------------------------------------------
    // WebView plumbing
    // ------------------------------------------------------------------

    private fun handleUrl(url: String): Boolean {
        return if (AppUtil.isDirectMediaUrl(url)) {
            openPlayer(url, webView.title ?: currentSource.name)
            true
        } else {
            false
        }
    }

    inner class BrowserClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            handleUrl(request.url.toString())

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            handleUrl(url)

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            progressBar.visibility = View.VISIBLE
            view?.evaluateJavascript(hooksJs, null)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            progressBar.visibility = View.GONE
            view?.evaluateJavascript(hooksJs, null)
        }
    }

    inner class ChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (title.isNullOrBlank()) {
                titleView.text = currentSource.name
            } else {
                titleView.text = title
            }
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            icon ?: return
            faviconStore.save(faviconStore.circleCrop(icon), currentSource.url)
            runOnUiThread { sourceAdapter.refreshIcons() }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            // Site requested fullscreen (in-page player): give it the whole screen.
            if (fullscreenView != null) {
                callback?.onCustomViewHidden()
                return
            }
            fullscreenView = view
            fullscreenCallback = callback
            chrome.visibility = View.GONE
            hintBar.visibility = View.GONE
            (findViewById<View>(R.id.rootLayout) as ViewGroup).addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            AppUtil.hideSystemUi(this@MainActivity)
        }

        override fun onHideCustomView() {
            onHideFullscreen()
        }
    }

    inner class VideoBridge {
        @android.webkit.JavascriptInterface
        fun onVideoUrl(url: String?, title: String?) {
            url ?: return
            runOnUiThread { handleDetectedVideo(url, title ?: "") }
        }
    }

    // ------------------------------------------------------------------
    // Remote / keyboard
    // ------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
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
                openSettings(); return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                webView.scrollBy(0, -webView.height * 3 / 4); return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                webView.scrollBy(0, webView.height * 3 / 4); return true
            }
        }

        // TV: D-pad drives the virtual pointer while the page has focus.
        if (isTv && (currentFocus == webView || currentFocus == null)) {
            // Pushing past the top edge moves focus to the source strip.
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && pointer.isAtTop()) {
                sourceStrip.requestFocus()
                return true
            }
            if (pointer.handleKey(keyCode)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fullscreenView != null) {
            exitFullscreen()
            return
        }
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun exitFullscreen() {
        try {
            fullscreenCallback?.onCustomViewHidden()
        } catch (e: Exception) {
            // Some web pages never registered a callback; ignore.
        }
        onHideFullscreen()
    }

    private fun onHideFullscreen() {
        fullscreenView?.let { (findViewById<View>(R.id.rootLayout) as ViewGroup).removeView(it) }
        fullscreenView = null
        fullscreenCallback = null
        chrome.visibility = View.VISIBLE
        hintBar.visibility = View.VISIBLE
    }

    private fun openSettings() {
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    private fun readAsset(name: String): String = try {
        assets.open(name).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        ""
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FORCE_SOURCE_URL = "force_url"
        private const val DETECT_DEBOUNCE_MS = 12_000L
        private const val PLAYER_COOLDOWN_MS = 25_000L
    }
}
