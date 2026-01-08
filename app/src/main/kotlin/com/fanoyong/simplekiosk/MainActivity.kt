package com.fanoyong.simplekiosk

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.http.SslError
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fanoyong.simplekiosk.ui.theme.SimpleKioskTheme

class MainActivity : ComponentActivity() {

    private val homeAssistantUrl = "http://homeassistant.local:8123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupKioskMode()
        hideSystemUI()

        setContent {
            SimpleKioskTheme {
                KioskScreen(url = homeAssistantUrl, activity = this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private fun setupKioskMode() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, KioskAdminReceiver::class.java)

        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            startLockTask()
        } else {
            try {
                startLockTask()
            } catch (_: Exception) {
                // todo
            }
        }
    }

    fun stopKioskMode() {
        try {
            stopLockTask()
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Cannot exit kiosk mode", Toast.LENGTH_SHORT).show()
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
}

@Composable
fun KioskScreen(url: String, activity: MainActivity) {
    var isLoading by remember { mutableStateOf(true) }
    var showExitDialog by remember { mutableStateOf(false) }

    val onLoadFinished = { isLoading = false }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        KioskWebView(url = url, onLoadFinished = onLoadFinished)

        AnimatedVisibility(
            visible = isLoading,
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connecting to Home...", color = Color.White)
            }
        }

        SecretExitButton(
            modifier = Modifier.align(Alignment.TopStart),
            onSecretTriggered = { showExitDialog = true }
        )

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("Exit Kiosk Mode?") },
                text = { Text("You are about to exit Kiosk mode and go to Settings.") },
                confirmButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        activity.stopKioskMode()
                    }) {
                        Text("EXIT")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KioskWebView(url: String, onLoadFinished: () -> Unit) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    textZoom = 100
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadFinished()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        val errorHtml = """
                            <body style='background:black;color:white;display:flex;justify-content:center;align-items:center;height:100vh;flex-direction:column;font-family:sans-serif;'>
                                <h2>Connection Lost</h2>
                                <p>Retrying in 5 seconds...</p>
                                <script>
                                    setTimeout(function(){ location.reload(); }, 5000);
                                </script>
                            </body>
                        """
                        view?.loadData(errorHtml, "text/html", "UTF-8")
                        onLoadFinished()
                    }

                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.proceed() // ignore internal IP SSL
                    }
                }
                loadUrl(url)
            }
        }
    )
}

@Composable
fun SecretExitButton(modifier: Modifier = Modifier, onSecretTriggered: () -> Unit) {
    var tapCount by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .size(60.dp)
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        tapCount++
                        if (tapCount >= 5) {
                            onSecretTriggered()
                            tapCount = 0
                        }
                    }
                )
            }
    )
}
