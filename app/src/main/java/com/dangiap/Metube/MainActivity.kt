package com.dangiap.Metube

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Rational
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.LinearLayout
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

        // Gọi hàm kiểm tra quyền Pin chạy ngầm khi vừa mở app
        checkBatteryOptimization()
    }

    // --- LOGIC KIỂM TRA VÀ XIN QUYỀN PIN ---
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val sharedPreferences = getSharedPreferences("MetubePrefs", Context.MODE_PRIVATE)
            // Kiểm tra xem người dùng đã tick "Không hiện lại" chưa
            val dontShowAgain = sharedPreferences.getBoolean("dontShowBatteryPrompt", false)

            if (dontShowAgain) return // Nếu đã tick, thoát hàm luôn, không hiện bảng

            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Nếu ứng dụng CHƯA có trong danh sách Không hạn chế
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryPrompt(sharedPreferences)
            }
        }
    }

    private fun showBatteryPrompt(sharedPreferences: android.content.SharedPreferences) {
        // Tự tạo Giao diện cho bảng thông báo (gồm 1 ô Checkbox)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val checkBox = CheckBox(this)
        checkBox.text = "Không hiển thị lại thông báo này"
        layout.addView(checkBox)

        // Hiển thị bảng thông báo
        AlertDialog.Builder(this)
            .setTitle("Cho phép chạy ngầm")
            .setMessage("Để video không bị tự động ngắt khi tắt màn hình, hãy cho phép ứng dụng chạy trong chế độ 'Không hạn chế' (Unrestricted) pin.")
            .setView(layout)
            .setPositiveButton("Bật ngay") { _, _ ->
                // Nếu tick vào ô, lưu lại trạng thái
                if (checkBox.isChecked) {
                    sharedPreferences.edit().putBoolean("dontShowBatteryPrompt", true).apply()
                }
                // Mở cửa sổ hệ thống để người dùng bấm "Cho phép"
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("Bỏ qua") { _, _ ->
                // Nếu tick vào ô và bấm bỏ qua, cũng lưu lại trạng thái
                if (checkBox.isChecked) {
                    sharedPreferences.edit().putBoolean("dontShowBatteryPrompt", true).apply()
                }
            }
            .setCancelable(false) // Bắt buộc phải chọn 1 trong 2 nút
            .show()
    }
    // ---------------------------------------

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
