package com.fanoyong.simplekiosk

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fanoyong.simplekiosk.ui.theme.SimpleKioskTheme

class MainActivity : ComponentActivity() {

    // TODO: 여기를 본인의 Home Assistant 주소로 변경하세요 (예: http://192.168.0.x:8123)
    private val HOME_ASSISTANT_URL = "http://homeassistant.local:8123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupKioskMode()
        hideSystemUI()

        setContent {
            SimpleKioskTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KioskWebView(url = HOME_ASSISTANT_URL)
                }
            }
        }
    }

    private fun setupKioskMode() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, KioskAdminReceiver::class.java)

        // execute LockTasks if it has Device Owner permission
        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            startLockTask()
        } else {
            // even without permission, try normal pinning mode
            try {
                startLockTask()
            } catch (_: Exception) {
                // todo
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KioskWebView(url: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true // For HA dashboard
                    domStorageEnabled = true // maintain logged-in session
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    textZoom = 100
                }

                webViewClient = object : WebViewClient() {
                    // don't execute external browser
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        return false
                    }

                    // ignore SSL cert
                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed()
                    }
                }

                loadUrl(url)
            }
        },
        update = { webView ->
            // todo
        }
    )
}
