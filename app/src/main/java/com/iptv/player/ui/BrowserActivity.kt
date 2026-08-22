package com.iptv.player.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Patterns
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.iptv.player.R
import com.iptv.player.databinding.ActivityBrowserBinding

/**
 * A minimal, self-contained in-app web browser. Lets the user navigate to any
 * site to look things up, sign in to services, etc. without leaving the app.
 * Deliberately generic - it does not bundle or suggest any particular sites.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding

    companion object {
        const val EXTRA_START_URL = "extra_start_url"
        private const val DEFAULT_SEARCH_URL = "https://www.google.com/search?q="
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.browser_title)

        val webView = binding.webView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.browserEmptyState.visibility = View.GONE
                binding.browserUrlInput.setText(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.browserUrlInput.setText(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.browserProgress.progress = newProgress
                binding.browserProgress.visibility =
                    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) this@BrowserActivity.title = title
            }
        }

        binding.browserBackButton.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        binding.browserForwardButton.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        binding.browserReloadButton.setOnClickListener { webView.reload() }

        binding.browserUrlInput.setOnEditorActionListener { _, actionId, event ->
            val triggered = actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (triggered) {
                navigateTo(binding.browserUrlInput.text.toString())
                true
            } else {
                false
            }
        }

        val startUrl = intent.getStringExtra(EXTRA_START_URL)
        if (!startUrl.isNullOrBlank()) {
            navigateTo(startUrl)
        } else {
            binding.browserEmptyState.visibility = View.VISIBLE
        }
    }

    /** Accepts a raw address bar entry: a full URL, a bare domain, or a search phrase. */
    private fun navigateTo(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        val resolved = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            Patterns.WEB_URL.matcher(trimmed).matches() && !trimmed.contains(" ") -> "https://$trimmed"
            else -> DEFAULT_SEARCH_URL + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
        binding.browserEmptyState.visibility = View.GONE
        binding.webView.loadUrl(resolved)
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            webViewClient = WebViewClient()
            webChromeClient = null
            destroy()
        }
        super.onDestroy()
    }
}
