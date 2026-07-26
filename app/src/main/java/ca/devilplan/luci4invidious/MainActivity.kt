package ca.devilplan.luci4invidious

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {

            // Provide hardcoded basic-auth credentials for every request
            // to the Invidious instance.
            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler,
                host: String?,
                realm: String?
            ) {
                handler.proceed(UrlConverter.INVIDIOUS_USER, UrlConverter.INVIDIOUS_PASS)
            }

            // Intercept any YouTube link clicked inside the WebView and
            // redirect it to Invidious instead.
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val converted = UrlConverter.convert(url)
                if (converted != null) {
                    view?.loadUrl(converted)
                    return true
                }
                return false
            }
        }

        // If the app was launched via a YouTube link, convert it.
        // Otherwise load the Invidious homepage.
        val targetUrl = intent?.data?.let { UrlConverter.convert(it.toString()) }
            ?: "https://${UrlConverter.INVIDIOUS_HOST}"

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
}