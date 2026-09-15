package converter.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import converter.android.ocr.OcrEngine
import converter.core.Price
import converter.core.readPrices
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** What the last analysed frame produced. */
data class FrameState(
    val prices: List<Price> = emptyList(),
    val framesSeen: Long = 0,
    val lastMillis: Long = 0,
)

/**
 * Live camera with the price rules attached.
 *
 * Frames are analysed on a single background thread with only the newest kept:
 * recognition costs hundreds of milliseconds, so a queue would only build a
 * backlog of stale frames. There is no attempt at 30fps, and there should not
 * be — the benchmark put PaddleOCR's mobile configuration near 800ms a frame on
 * a desktop CPU.
 */
@Composable
fun CameraScreen(engine: OcrEngine, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            var frame by remember { mutableStateOf(FrameState()) }
            CameraPreview(engine) { frame = it }
            ReadingPanel(
                engine = engine,
                frame = frame,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            PermissionPrompt(
                onAsk = { ask.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun CameraPreview(engine: OcrEngine, onFrame: (FrameState) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val seen = remember { AtomicLong(0) }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { image ->
                    val started = System.currentTimeMillis()
                    try {
                        val lines = engine.recognize(image.toBitmap())
                        val prices = readPrices(lines)
                        onFrame(
                            FrameState(
                                prices = prices,
                                framesSeen = seen.incrementAndGet(),
                                lastMillis = System.currentTimeMillis() - started,
                            )
                        )
                    } finally {
                        image.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

@Composable
private fun ReadingPanel(engine: OcrEngine, frame: FrameState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            // The window is edge to edge, so the panel has to clear the gesture
            // bar itself; the background stays behind it, the text does not.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (frame.prices.isEmpty()) {
            Text(
                text = "No price in view",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            for (price in frame.prices) {
                Text(
                    text = "${price.amount} ${price.code}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Text(
            text = "engine: ${engine.name} · frames: ${frame.framesSeen} · ${frame.lastMillis} ms",
            color = Color(0xFF9E9E9E),
            style = MaterialTheme.typography.bodySmall,
        )
        if (!engine.readsCyrillic) {
            Text(
                // Not a disclaimer for its own sake: a Latin-only recognizer
                // corrupts Cyrillic prices rather than missing them.
                text = "This engine cannot read Cyrillic prices",
                color = Color(0xFFFFB74D),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onAsk: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "How Much? reads prices through the camera.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onAsk) { Text("Allow camera") }
    }
}
