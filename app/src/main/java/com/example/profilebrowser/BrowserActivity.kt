package com.example.profilebrowser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.ProfileStore
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class BrowserActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var profile: Profile
    private var proxy: ProxyInfo? = null
    private var proxySet = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Store.find(this, intent.getStringExtra("id") ?: "")
        if (p == null) { finish(); return }
        profile = p
        title = "${p.name} (${p.os})"

        // ---- UI ----
        urlBar = EditText(this).apply {
            setSingleLine()
            hint = "URL ya search"
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        val go = Button(this).apply { text = "Go" }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(urlBar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(go)
        }
        webView = WebView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        // ---- isolated storage per profile (cookies, localStorage, cache) ----
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getOrCreateProfile(profile.webProfileName)
            WebViewCompat.setProfile(webView, profile.webProfileName)
        } else {
            Toast.makeText(this, "Is phone ke WebView me alag profile storage support nahi. " +
                    "Android System WebView / Chrome update karo.", Toast.LENGTH_LONG).show()
        }

        // ---- settings + fake UA ----
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = PRESETS.getValue(profile.os).ua
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        // ---- fingerprint JS before any page script runs ----
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, FingerprintJs.build(profile), setOf("*"))
        } else {
            Toast.makeText(this, "WebView purana hai: fingerprint spoof limited (sirf User-Agent)", Toast.LENGTH_LONG).show()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                urlBar.setText(url)
            }

            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
                val px = proxy
                if (px?.user != null && host.equals(px.host, ignoreCase = true)) {
                    handler.proceed(px.user, px.pass ?: "")
                } else super.onReceivedHttpAuthRequest(view, handler, host, realm)
            }
        }

        go.setOnClickListener { load(urlBar.text.toString()) }
        urlBar.setOnEditorActionListener { _, id, ev ->
            if (id == EditorInfo.IME_ACTION_GO || ev?.keyCode == KeyEvent.KEYCODE_ENTER) {
                load(urlBar.text.toString()); true
            } else false
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        // ---- proxy (app-wide override, so only one proxied profile at a time) ----
        try {
            proxy = parseProxy(profile.proxy)
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
        val px = proxy
        if (px == null) {
            load(profile.url)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            val cfg = ProxyConfig.Builder().addProxyRule(px.rule()).build()
            ProxyController.getInstance().setProxyOverride(cfg, ContextCompat.getMainExecutor(this)) {
                proxySet = true
                load(profile.url)
            }
        } else {
            Toast.makeText(this, "Is WebView me proxy support nahi. Browser load NAHI kiya (leak se bachne ke liye).", Toast.LENGTH_LONG).show()
        }
    }

    private fun load(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        }
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        if (proxySet) {
            ProxyController.getInstance().clearProxyOverride(ContextCompat.getMainExecutor(this)) {}
        }
        webView.destroy()
        super.onDestroy()
    }
}
