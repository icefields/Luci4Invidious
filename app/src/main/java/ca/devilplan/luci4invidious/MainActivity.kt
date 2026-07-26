package ca.devilplan.luci4invidious

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var urlConverter: UrlConverter

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Read credentials from BuildConfig (injected from secrets.properties at build time)
        urlConverter = UrlConverter(BuildConfig.INVIDIOUS_HOST)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

        // Disable Google Safe Browsing — it leaks every URL to Google.
        // Available from API 26; guard for API 24 compatibility.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = false
        }

        // Enable JS dialogs (alert/confirm/prompt).
        // NOTE: Fullscreen <video> requires overriding onShowCustomView/
        // onHideCustomView in a custom WebChromeClient — not implemented
        // in this minimal app.
        webView.webChromeClient = android.webkit.WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            // Provide basic-auth credentials ONLY for the Invidious host.
            // Sending credentials to any other host would be a credential leak.
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

            // Intercept any YouTube link clicked inside the WebView and
            // redirect it to Invidious instead.
            // Non-YouTube, non-Invidious links are handed to the system browser
            // so they don't get trapped inside the WebView.
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                // YouTube link → convert to Invidious and load in WebView
                val converted = urlConverter.convert(url)
                if (converted != null) {
                    if (view != null) {
                        view.loadUrl(converted)
                        return true
                    }
                    return false
                }

                // Already an Invidious link → let the WebView handle it
                if (urlConverter.isInvidiousHost(url)) {
                    return false
                }

                // External link → open in the system browser, don't trap it
                view?.context?.let {
                    val extIntent = Intent(
                        Intent.ACTION_VIEW,
                        request.url
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        it.startActivity(extIntent)
                        return true
                    } catch (e: ActivityNotFoundException) {
                        // No app can handle this scheme — let WebView try
                    }
                }
                return false
            }
        }

        // If the app was launched via a YouTube link, convert it.
        // Otherwise load the Invidious homepage.
        val targetUrl = intent?.data?.let { urlConverter.convert(it.toString()) }
            ?: "https://${BuildConfig.INVIDIOUS_HOST}"

        webView.loadUrl(targetUrl)

        // Handle back button: navigate WebView history first, then exit.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
        // Prevent WebView memory leak: detach from parent and destroy.
        if (::webView.isInitialized) {
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}