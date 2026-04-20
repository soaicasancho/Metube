package com.dangiap.Metube

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView

    private val adBlockList = listOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "youtube.com/api/stats/ads",
        "youtube.com/pagead"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.myWebView)
        setupWebView()

        webView.loadUrl("https://m.youtube.com")
    }

    private fun setupWebView() {
        val settings = webView.settings
        
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false 
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36"

        webView.webChromeClient = WebChromeClient()
        
        webView.webViewClient = object : WebViewClient() {
            
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url.toString()
                
                for (adDomain in adBlockList) {
                    if (url.contains(adDomain)) {
                        val emptyStream = ByteArrayInputStream("".toByteArray())
                        return WebResourceResponse("text/plain", "UTF-8", emptyStream)
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectJavaScript()
            }
        }
    }

    private fun injectJavaScript() {
        val jsCode = """
            javascript:(function() {
                var style = document.createElement('style');
                style.innerHTML = 'ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer, .video-ads, .ad-showing { display: none !important; }';
                document.head.appendChild(style);

                Object.defineProperty(document, 'hidden', {value: false, writable: false});
                Object.defineProperty(document, 'visibilityState', {value: 'visible', writable: false});
                
                window.addEventListener('visibilitychange', function(e) {
                    e.stopImmediatePropagation();
                }, true);
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val pipBuilder = PictureInPictureParams.Builder().setAspectRatio(aspectRatio)
            enterPictureInPictureMode(pipBuilder.build())
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            webView.evaluateJavascript("javascript:document.querySelector('ytm-header-bar').style.display='none';", null)
        } else {
            webView.evaluateJavascript("javascript:document.querySelector('ytm-header-bar').style.display='block';", null)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
