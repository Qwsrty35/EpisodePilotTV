package com.example.episodepilottv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {
    companion object {
        private const val DEFAULT_URL = "https://www.wcostream.tv/naruto-shippuden-episode-18-english-dubbed-2"
        private const val PREFS = "episode_pilot"
        private const val KEY_URL = "last_url"
        private const val KEY_AUTONEXT = "auto_next"
    }

    private lateinit var root: FrameLayout
    private lateinit var browser: WebView
    private lateinit var playerView: PlayerView
    private lateinit var controls: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var urlBox: EditText
    private lateinit var autoNextButton: Button
    private lateinit var statusView: TextView

    private var player: ExoPlayer? = null
    private var currentPageUrl: String = DEFAULT_URL
    private var lastMediaUrl: String? = null
    private var autoNext = true
    private var launchingMedia = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        currentPageUrl = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        autoNext = prefs.getBoolean(KEY_AUTONEXT, true)

        buildUi()
        configureWebView()
        updateEpisodeLabel()
        loadEpisode(currentPageUrl)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun textButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(52)
        setOnClickListener { action() }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        browser = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.VISIBLE
        }
        root.addView(browser, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            useController = true
            controllerAutoShow = true
            visibility = View.GONE
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(playerView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(0xD9000000.toInt())
        }
        root.addView(controls, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))

        titleView = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(6))
        }
        controls.addView(titleView)

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        urlBox = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFAAAAAA.toInt())
            hint = "Paste an episode page URL"
        }
        addressRow.addView(urlBox, LinearLayout.LayoutParams(0, dp(54), 1f))
        addressRow.addView(textButton("Load") {
            val candidate = urlBox.text.toString().trim()
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                loadEpisode(candidate)
            } else {
                toast("Enter a full http:// or https:// URL")
            }
        })
        controls.addView(addressRow)

        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val prev = textButton("⏮ Previous") { shiftEpisode(-1) }
        val replay = textButton("↻ Replay") {
            lastMediaUrl?.let { playMedia(it) } ?: browser.reload()
        }
        val next = textButton("Next ⏭") { shiftEpisode(+1) }
        autoNextButton = textButton("") {
            autoNext = !autoNext
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_AUTONEXT, autoNext).apply()
            updateAutoNextLabel()
        }
        listOf(prev, replay, next, autoNextButton).forEach {
            navRow.addView(it, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        controls.addView(navRow)

        statusView = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, dp(4), 0, 0)
        }
        controls.addView(statusView)
        updateAutoNextLabel()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentString + " EpisodePilotTV/1.0"
            builtInZoomControls = false
            displayZoomControls = false
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)

        browser.webChromeClient = object : WebChromeClient() {}
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                request?.let { inspectRequest(it) }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank() && url.startsWith("http")) {
                    currentPageUrl = url
                    rememberUrl(url)
                    runOnUiThread {
                        urlBox.setText(url)
                        updateEpisodeLabel()
                        setStatus("Page loaded. Looking for a playable stream…")
                    }
                }
                handler.postDelayed({
                    browser.evaluateJavascript(
                        "(function(){var vids=[].slice.call(document.querySelectorAll('video'));vids.forEach(function(v){try{v.play();}catch(e){}});return vids.length;})();",
                        null
                    )
                }, 1200)
            }
        }
    }

    private fun inspectRequest(request: WebResourceRequest) {
        val url = request.url.toString()
        val lower = url.lowercase()
        val accept = request.requestHeaders["Accept"].orEmpty().lowercase()
        val mediaLike = lower.contains(".m3u8") ||
            lower.contains(".mpd") ||
            lower.matches(Regex(".*\\.(mp4|m4v|webm)(\\?.*)?$")) ||
            accept.contains("application/vnd.apple.mpegurl") ||
            accept.contains("application/x-mpegurl")

        if (mediaLike && !launchingMedia && url != lastMediaUrl) {
            runOnUiThread {
                lastMediaUrl = url
                setStatus("Stream detected. Opening native player…")
                playMedia(url)
            }
        }
    }

    private fun loadEpisode(url: String) {
        releasePlayer()
        launchingMedia = false
        lastMediaUrl = null
        currentPageUrl = url
        rememberUrl(url)
        urlBox.setText(url)
        updateEpisodeLabel()
        playerView.visibility = View.GONE
        browser.visibility = View.VISIBLE
        browser.loadUrl(url)
        browser.requestFocus()
        setStatus("Loading episode page…")
    }

    private fun shiftEpisode(delta: Int) {
        val shifted = EpisodeUrl.shift(currentPageUrl, delta)
        if (shifted == null) {
            toast("I couldn't find an episode number in this URL.")
            return
        }
        loadEpisode(shifted)
    }

    private fun playMedia(mediaUrl: String) {
        if (launchingMedia) return
        launchingMedia = true
        releasePlayer()

        val headers = mutableMapOf<String, String>()
        CookieManager.getInstance().getCookie(currentPageUrl)?.let { headers["Cookie"] = it }
        headers["Referer"] = currentPageUrl
        headers["User-Agent"] = browser.settings.userAgentString

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)

        val newPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player = newPlayer
        playerView.player = newPlayer

        val builder = MediaItem.Builder().setUri(Uri.parse(mediaUrl))
        when {
            mediaUrl.lowercase().contains(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            mediaUrl.lowercase().contains(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }

        newPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        launchingMedia = false
                        setStatus("Playing in native player")
                    }
                    Player.STATE_ENDED -> {
                        launchingMedia = false
                        if (autoNext) handler.postDelayed({ shiftEpisode(+1) }, 700)
                        else {
                            setStatus("Episode finished")
                            controls.visibility = View.VISIBLE
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                launchingMedia = false
                setStatus("Native playback failed: ${error.errorCodeName}. Press Back to use the webpage player.")
                controls.visibility = View.VISIBLE
            }
        })

        newPlayer.setMediaItem(builder.build())
        newPlayer.prepare()
        newPlayer.playWhenReady = true
        browser.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        controls.visibility = View.GONE
        playerView.requestFocus()
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
        launchingMedia = false
    }

    private fun updateEpisodeLabel() {
        val ep = EpisodeUrl.episodeNumber(currentPageUrl)
        titleView.text = if (ep != null) {
            val shown = if (ep % 1.0 == 0.0) ep.toInt().toString() else ep.toString()
            "Episode Pilot TV  •  Episode $shown"
        } else "Episode Pilot TV"
    }

    private fun updateAutoNextLabel() {
        autoNextButton.text = if (autoNext) "Auto-next: ON" else "Auto-next: OFF"
    }

    private fun rememberUrl(url: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, url).apply()
    }

    private fun setStatus(message: String) { statusView.text = message }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> { shiftEpisode(+1); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { shiftEpisode(-1); return true }
                KeyEvent.KEYCODE_MENU -> {
                    controls.visibility = if (controls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    if (controls.visibility == View.VISIBLE) autoNextButton.requestFocus()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            playerView.visibility == View.VISIBLE -> {
                releasePlayer()
                playerView.visibility = View.GONE
                browser.visibility = View.VISIBLE
                controls.visibility = View.VISIBLE
                browser.requestFocus()
                setStatus("Webpage player available. Select Next/Previous from the controls below.")
            }
            browser.canGoBack() -> browser.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        browser.destroy()
        super.onDestroy()
    }
}
