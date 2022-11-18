package com.example.myapplication

import android.Manifest
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.MainActivity.Companion.byteArray
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {

    companion object {
        private const val PORT = 5001
        lateinit var byteArray: ByteArray
        lateinit var mediaPlayer: MediaPlayer
    }

    private var count = 0

    private val server by lazy {
        embeddedServer(Netty, PORT, watchPaths = emptyList()) {
            install(WebSockets)
            install(CallLogging)
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                    disableHtmlEscaping()
                }
            }
            install(CORS) {
                method(HttpMethod.Get)
                method(HttpMethod.Post)
                method(HttpMethod.Delete)
                anyHost()
            }
            install(Compression) {
                gzip()
            }
            routing {
                get("/") {
                    call.respond(byteArray)
                }
                get("/beep") {
                    Thread {
                        mediaPlayer.start()
                    }.start()
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPlayer = MediaPlayer.create(this, R.raw.beep)
        mediaPlayer.setOnCompletionListener {
            count++
            if (count < 2) {
                it.start()
            } else {
                count = 0
            }
        }
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wInfo = wifiManager.connectionInfo
        val ipAddress = Formatter.formatIpAddress(wInfo.ipAddress)
        CoroutineScope(Dispatchers.IO).launch {
            server.start(wait = true)
        }
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Permission(
                        permission = Manifest.permission.CAMERA,
                    ) {
                        CameraPreview(ipAddress)
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(ip: String) {
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxWidth(),
            factory = { context ->
                val previewView = PreviewView(context).apply {
                    this.scaleType = PreviewView.ScaleType.FIT_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val imageCapture = ImageCapture.Builder().build()

                val imageAnalysis = ImageAnalysis.Builder().build()
                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    val yBuffer = imageProxy.planes[0].buffer // Y
                    val vuBuffer = imageProxy.planes[2].buffer // VU

                    val ySize = yBuffer.remaining()
                    val vuSize = vuBuffer.remaining()

                    val nv21 = ByteArray(ySize + vuSize)

                    yBuffer.get(nv21, 0, ySize)
                    vuBuffer.get(nv21, ySize, vuSize)

                    val yuvImage =
                        YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
                    val out = ByteArrayOutputStream()
                    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 90, out)
                    byteArray = out.toByteArray()
                    imageProxy.close()
                }


                val previewUseCase = androidx.camera.core.Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                coroutineScope.launch {
                    val cameraProvider = context.getCameraProvider()
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            previewUseCase,
                            imageCapture, imageAnalysis
                        )
                    } catch (ex: Exception) {
                        Log.e("CameraPreview", "Use case binding failed", ex)
                    }
                }
                previewView
            }

        )
        SelectionContainer(
            modifier = Modifier
                .weight(0.1F)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            Text(
                text = ip,
                color = Color.Yellow,
                textAlign = TextAlign.Center
            )
        }
    }
}