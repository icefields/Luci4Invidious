package com.devilplan.luci4invidious

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var urlConverter: UrlConverter
    private lateinit var rootContainer: ViewGroup

    private var customView: View? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.root_container)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.navigationBars()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        urlConverter = UrlConverter(BuildConfig.INVIDIOUS_HOST)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = false
        }

        val authHeader = "Basic " + Base64.encodeToString(
            "${BuildConfig.INVIDIOUS_USER}:${BuildConfig.INVIDIOUS_PASS}".toByteArray(),
            Base64.NO_WRAP
        )

        webView.setDownloadListener(DownloadListener { url, _, _, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setTitle("Invidious download")
                    .addRequestHeader("Authorization", authHeader)
                if (mimetype != null) {
                    request.setMimeType(mimetype)
                }
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
            } catch (e: Exception) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(browserIntent)
                } catch (e2: ActivityNotFoundException) {
                    // Nothing we can do
                }
            }
        })

        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view

                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                rootContainer.addView(
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
                    rootContainer.removeView(it)
                    customView = null
                }
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                webView.visibility = View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler,
                host: String?,
                realm: String?
            ) {
                if (host == BuildConfig.INVIDIOUS_HOST) {
                    handler.proceed(BuildConfig.INVIDIOUS_USER, BuildConfig.INVIDIOUS_PASS)
                } else {
                    handler.cancel()
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
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

                if (urlConverter.isInvidiousHost(url)) {
                    return false
                }

                view?.context?.let {
                    val extIntent = Intent(
                        Intent.ACTION_VIEW,
                        req.url
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        it.startActivity(extIntent)
                        return true
                    } catch (e: ActivityNotFoundException) {
                        // No app can handle this scheme
                    }
                }
                return false
            }
        }

        val targetUrl = intent?.data?.let { urlConverter.convert(it.toString()) }
            ?: "https://${BuildConfig.INVIDIOUS_HOST}"

        webView.loadUrl(targetUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                    return
                }
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) {
            webView.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            customView?.let {
                rootContainer.removeView(it)
                customView = null
            }
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}