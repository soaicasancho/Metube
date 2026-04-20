package com.dangiap.Metube

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
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // Danh sách đen chặn quảng cáo (AdBlock)
    private val adBlockList = listOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "youtube.com/api/stats/ads",
        "youtube.com/pagead"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lưu ý: R.layout.activity_main liên kết với file giao diện bạn vừa tạo ở bước trước
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.myWebView)
        setupWebView()

        // Tải trang chủ YouTube Mobile
        webView.loadUrl("https://m.youtube.com")
    }

    private fun setupWebView() {
        val settings = webView.settings
        
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false 
        // Giả lập điện thoại Android để lấy đúng giao diện mobile
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36"

        webView.webChromeClient = WebChromeClient()
        
        webView.webViewClient = object : WebViewClient() {
            
            // LOGIC CHẶN QUẢNG CÁO MẠNG
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

            // TIÊM JAVASCRIPT SAU KHI TẢI TRANG XONG
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectJavaScript()
            }
        }
    }

    private fun injectJavaScript() {
        // Mã JS giúp ẩn khung quảng cáo và cho phép phát dưới nền (tắt màn hình)
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

    // XỬ LÝ HÌNH TRONG HÌNH (PiP)
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

    // Nút Back trên điện thoại lùi lại trang web thay vì thoát app ngay
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
