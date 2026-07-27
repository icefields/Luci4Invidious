package com.devilplan.luci4invidious

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private var webViewRef: WebView? = null
    private var customView: View? = null
    private var rootContainer: ViewGroup? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val urlConverter = UrlConverter(BuildConfig.INVIDIOUS_HOST)
        val intentUrl = intent?.data?.toString()
        val activity = this

        val authHeader = "Basic " + Base64.encodeToString(
            "${BuildConfig.INVIDIOUS_USER}:${BuildConfig.INVIDIOUS_PASS}".toByteArray(),
            Base64.NO_WRAP
        )

        // JS patch: adds Authorization header to all fetch() and XHR requests.
        // Invidious's video.js player uses MSE which fetches audio/video
        // segments via fetch(). shouldInterceptRequest does NOT fire for
        // fetch(), so this is the only way to auth those requests.
        // Preserves existing headers (including Range) by merging.
        val jsAuthPatch = """
            (function() {
                var auth = "$authHeader";
                var origFetch = window.fetch;
                if (origFetch) {
                    window.fetch = function(input, init) {
                        init = init || {};
                        var h = new Headers();
                        if (input instanceof Request) {
                            input.headers.forEach(function(v, k) { h.set(k, v); });
                        }
                        if (init.headers instanceof Headers) {
                            init.headers.forEach(function(v, k) { h.set(k, v); });
                        } else if (init.headers && typeof init.headers === 'object') {
                            Object.keys(init.headers).forEach(function(k) {
                                h.set(k, init.headers[k]);
                            });
                        }
                        if (!h.has('Authorization')) h.set('Authorization', auth);
                        init.headers = h;
                        return origFetch.call(this, input, init);
                    };
                }
                var origOpen = XMLHttpRequest.prototype.open;
                var origSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.open = function() {
                    this._authDone = false;
                    return origOpen.apply(this, arguments);
                };
                XMLHttpRequest.prototype.send = function(body) {
                    if (!this._authDone) {
                        try { this.setRequestHeader('Authorization', auth); this._authDone = true; } catch(e) {}
                    }
                    return origSend.apply(this, arguments);
                };
            })();
        """.trimIndent()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val root = FrameLayout(ctx)
                            root.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            rootContainer = root

                            val webView = WebView(ctx)

                            webView.settings.javaScriptEnabled = true
                            webView.settings.domStorageEnabled = true
                            webView.settings.useWideViewPort = true
                            webView.settings.loadWithOverviewMode = true
                            webView.settings.mediaPlaybackRequiresUserGesture = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                webView.settings.safeBrowsingEnabled = false
                            }

                            webView.webViewClient = object : WebViewClient() {

                                // 1. Inject auth patch before page scripts run
                                // and re-inject after each navigation.
                                override fun onPageStarted(
                                    view: WebView?, url: String?, favicon: android.graphics.Bitmap?
                                ) {
                                    view?.evaluateJavascript(jsAuthPatch, null)
                                }

                                override fun onPageFinished(
                                    view: WebView?, url: String?
                                ) {
                                    view?.evaluateJavascript(jsAuthPatch, null)
                                }

                                // 2. Handle 401 challenges for page loads and
                                // resource loads that go through Chromium's
                                // network stack.
                                override fun onReceivedHttpAuthRequest(
                                    view: WebView?, handler: HttpAuthHandler,
                                    host: String?, realm: String?
                                ) {
                                    handler.proceed(
                                        BuildConfig.INVIDIOUS_USER,
                                        BuildConfig.INVIDIOUS_PASS
                                    )
                                }

                                // 3. Intercept non-fetch resource requests
                                // (<video>, <audio>, <img>, XHR) and add auth.
                                // Strip Content-Encoding/Content-Length from
                                // response because HttpURLConnection
                                // auto-decompresses gzip.
                                override fun shouldInterceptRequest(
                                    view: WebView?, request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val req = request ?: return null
                                    val url = req.url.toString()

                                    if (!urlConverter.isInvidiousHost(url)) return null
                                    if (req.method != "GET") return null

                                    return try {
                                        val conn = URL(url).openConnection() as HttpURLConnection
                                        conn.requestMethod = "GET"
                                        conn.setRequestProperty("Authorization", authHeader)
                                        for ((key, value) in req.requestHeaders) {
                                            if (!key.equals("Authorization", ignoreCase = true)) {
                                                conn.setRequestProperty(key, value)
                                            }
                                        }
                                        conn.connectTimeout = 15000
                                        conn.readTimeout = 30000
                                        conn.instanceFollowRedirects = true
                                        conn.connect()

                                        val statusCode = conn.responseCode
                                        val contentType = conn.contentType ?: "application/octet-stream"
                                        val mimeType = contentType.substringBefore(';').trim()

                                        val stream: InputStream = if (statusCode in 200..399) {
                                            conn.inputStream
                                        } else {
                                            conn.errorStream ?: conn.inputStream
                                        }

                                        val response = WebResourceResponse(mimeType, null, stream)
                                        response.setStatusCodeAndReasonPhrase(
                                            statusCode,
                                            conn.responseMessage ?: ""
                                        )
                                        val respHeaders = mutableMapOf<String, String>()
                                        for ((key, values) in conn.headerFields) {
                                            if (key == null || values.isEmpty()) continue
                                            if (key.equals("Content-Encoding", ignoreCase = true)) continue
                                            if (key.equals("Content-Length", ignoreCase = true)) continue
                                            respHeaders[key] = values.joinToString(", ")
                                        }
                                        response.responseHeaders = respHeaders
                                        response
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?, request: WebResourceRequest?
                                ): Boolean {
                                    val req = request ?: return false
                                    val url = req.url.toString()

                                    val converted = urlConverter.convert(url)
                                    if (converted != null) {
                                        if (view != null) {
                                            view.loadUrl(converted)
                                            return true
                                        }
                                        return false
                                    }
                                    if (urlConverter.isInvidiousHost(url)) return false

                                    view?.context?.let {
                                        val extIntent = Intent(
                                            Intent.ACTION_VIEW, req.url
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            it.startActivity(extIntent)
                                            return true
                                        } catch (e: ActivityNotFoundException) {}
                                    }
                                    return false
                                }
                            }

                            webView.webChromeClient = object : WebChromeClient() {
                                override fun onShowCustomView(
                                    view: View?, callback: CustomViewCallback?
                                ) {
                                    if (customView != null) {
                                        callback?.onCustomViewHidden()
                                        return
                                    }
                                    if (view == null) return
                                    customView = view
                                    val controller = WindowInsetsControllerCompat(
                                        activity.window, activity.window.decorView
                                    )
                                    controller.hide(WindowInsetsCompat.Type.systemBars())
                                    controller.systemBarsBehavior =
                                        WindowInsetsControllerCompat
                                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    root.addView(
                                        view,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                    webView.visibility = View.GONE
                                }

                                override fun onHideCustomView() {
                                    customView?.let {
                                        root.removeView(it)
                                        customView = null
                                    }
                                    val controller = WindowInsetsControllerCompat(
                                        activity.window, activity.window.decorView
                                    )
                                    controller.show(WindowInsetsCompat.Type.systemBars())
                                    webView.visibility = View.VISIBLE
                                }
                            }

                            val targetUrl = intentUrl?.let { urlConverter.convert(it) }
                                ?: "https://${BuildConfig.INVIDIOUS_HOST}"
                            webView.loadUrl(targetUrl)

                            root.addView(
                                webView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            webViewRef = webView
                            root
                        }
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webViewRef
                if (customView != null) {
                    wv?.webChromeClient?.onHideCustomView()
                    return
                }
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        webViewRef?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webViewRef?.onResume()
    }

    override fun onDestroy() {
        webViewRef?.let { wv ->
            customView?.let { rootContainer?.removeView(it); customView = null }
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webViewRef = null
        super.onDestroy()
    }
}