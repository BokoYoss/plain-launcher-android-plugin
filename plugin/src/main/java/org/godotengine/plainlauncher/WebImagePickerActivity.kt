package org.godotengine.plainlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class WebImagePickerActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var useButton: Button
    private lateinit var cancelButton: Button
    private var pendingImageUrl: String? = null
    private var gameName: String = ""

    inner class ImageBridge {
        @JavascriptInterface
        fun onImageLongPressed(url: String) {
            Log.i("WebImagePicker", "JS image URL: $url")
            runOnUiThread {
                if (url.isNotEmpty()) {
                    pendingImageUrl = url
                    showConfirmBar()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameName = intent.getStringExtra("gameName") ?: ""
        val searchUrl = intent.getStringExtra("searchUrl") ?: "https://www.google.com"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                userAgentString = "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            addJavascriptInterface(ImageBridge(), "Android")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                override fun onPageFinished(view: WebView, url: String) {
                    injectImageListener()
                }
            }
            setDownloadListener { url, _, _, mimetype, _ ->
                val isImage = mimetype.startsWith("image/") ||
                    listOf(".jpg", ".jpeg", ".png", ".webp").any { url.contains(it, ignoreCase = true) }
                if (isImage) {
                    Log.i("WebImagePicker", "Download listener URL: $url")
                    pendingImageUrl = url
                    showConfirmBar()
                }
            }
            // Fallback: native hit test for browsers that block contextmenu JS
            setOnLongClickListener {
                val result = hitTestResult
                val imageUrl = when (result.type) {
                    WebView.HitTestResult.IMAGE_TYPE,
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> result.extra
                    else -> null
                }
                Log.i("WebImagePicker", "HitTest type=${result.type} url=$imageUrl")
                if (!imageUrl.isNullOrEmpty() && pendingImageUrl != imageUrl) {
                    pendingImageUrl = imageUrl
                    showConfirmBar()
                }
                false
            }
            loadUrl(searchUrl)
        }

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(230, 20, 20, 20))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        cancelButton = Button(this).apply {
            text = "Cancel"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(255, 120, 40, 40))
            visibility = View.GONE
            setOnClickListener {
                pendingImageUrl = null
                showHintBar()
            }
        }

        useButton = Button(this).apply {
            text = "Use image"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(255, 40, 130, 40))
            visibility = View.GONE
            setOnClickListener { downloadAndReturn() }
        }

        bottomBar.addView(statusText)
        bottomBar.addView(cancelButton)
        bottomBar.addView(useButton)

        root.addView(webView)
        root.addView(bottomBar)
        setContentView(root)

        showHintBar()
    }

    private fun injectImageListener() {
        webView.evaluateJavascript("""
            (function() {
                if (window._plImageListenerAttached) return;
                window._plImageListenerAttached = true;
                document.addEventListener('contextmenu', function(e) {
                    var el = e.target;
                    var url = '';
                    if (el.tagName === 'IMG') {
                        url = el.src || el.getAttribute('data-src') || el.getAttribute('data-lazy-src') || '';
                    } else {
                        var img = el.querySelector('img') || (el.parentElement && el.parentElement.querySelector('img'));
                        if (img) url = img.src || img.getAttribute('data-src') || '';
                    }
                    if (url && url !== window.location.href) {
                        Android.onImageLongPressed(url);
                    }
                }, true);
            })();
        """, null)
    }

    private fun showHintBar() {
        statusText.text = if (gameName.isNotEmpty())
            "Long-press any image to use as art for $gameName"
        else
            "Long-press any image to use it"
        useButton.visibility = View.GONE
        cancelButton.visibility = View.GONE
    }

    private fun showConfirmBar() {
        statusText.text = "Use this image?"
        useButton.visibility = View.VISIBLE
        cancelButton.visibility = View.VISIBLE
    }

    private fun downloadAndReturn() {
        val url = pendingImageUrl
        if (url.isNullOrEmpty()) {
            statusText.text = "No image URL captured — try long-pressing again"
            return
        }

        if (url.startsWith("data:image")) {
            handleDataUrl(url)
            return
        }

        statusText.text = "Downloading..."
        useButton.visibility = View.GONE
        cancelButton.visibility = View.GONE

        val cookies = CookieManager.getInstance().getCookie(url)
        val referer = webView.url ?: url

        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                if (!cookies.isNullOrEmpty()) {
                    connection.setRequestProperty("Cookie", cookies)
                }
                connection.setRequestProperty("Referer", referer)
                connection.connect()

                val code = connection.responseCode
                if (code !in 200..299) {
                    runOnUiThread {
                        statusText.text = "Server returned $code — try another image"
                        pendingImageUrl = null
                        cancelButton.visibility = View.VISIBLE
                    }
                    return@Thread
                }

                val bytes = connection.inputStream.readBytes()
                connection.disconnect()
                val tempFile = File(cacheDir, "chosen_art.tmp")
                tempFile.writeBytes(bytes)
                Log.i("WebImagePicker", "Downloaded ${bytes.size} bytes to ${tempFile.absolutePath}")
                runOnUiThread {
                    val result = Intent().putExtra("path", tempFile.absolutePath)
                    setResult(Activity.RESULT_OK, result)
                    finish()
                }
            } catch (e: Exception) {
                Log.e("WebImagePicker", "Download failed: ${e.message}")
                runOnUiThread {
                    statusText.text = "Download failed — try another image"
                    pendingImageUrl = null
                    cancelButton.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun handleDataUrl(dataUrl: String) {
        try {
            val base64Data = dataUrl.substringAfter("base64,")
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val tempFile = File(cacheDir, "chosen_art.tmp")
            tempFile.writeBytes(bytes)
            val result = Intent().putExtra("path", tempFile.absolutePath)
            setResult(Activity.RESULT_OK, result)
            finish()
        } catch (e: Exception) {
            statusText.text = "Could not use this image — try another"
            pendingImageUrl = null
            cancelButton.visibility = View.VISIBLE
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
