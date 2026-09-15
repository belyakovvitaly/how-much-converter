package converter.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import converter.android.ocr.OcrEngine
import converter.core.LocatedPrice
import converter.core.Price
import converter.core.PriceContext
import converter.core.RateTable
import converter.core.VoteSettings
import converter.core.VoteState
import converter.core.observe
import converter.core.convert
import converter.core.formatConverted
import converter.core.locatePrices
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

/** What the frames so far agree on. */
data class FrameState(
    /** Confirmed by several frames, not just read once — see [VoteState]. */
    val prices: List<LocatedPrice> = emptyList(),
    /** The analysed frame's size, which the overlay maps onto the view. */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    /** How many readings the last frame produced, confirmed or not. */
    val readLastFrame: Int = 0,
    /** How many of the last frame's lines were carried rather than read. */
    val reusedLastFrame: Int = 0,
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
fun CameraScreen(
    engine: OcrEngine,
    rates: RateTable?,
    /** What the prices in view are in; null when the place is unknown. */
    source: String?,
    /** What to convert into. */
    target: String,
    onChangeSource: () -> Unit = {},
    onChangeTarget: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
            CameraPreview(engine, source) { frame = it }
            PriceOverlay(
                prices = frame.prices,
                imageWidth = frame.imageWidth,
                imageHeight = frame.imageHeight,
                rates = rates,
                target = target,
            )
            ReadingPanel(
                engine = engine,
                frame = frame,
                rates = rates,
                source = source,
                target = target,
                onChangeSource = onChangeSource,
                onChangeTarget = onChangeTarget,
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
private fun CameraPreview(
    engine: OcrEngine,
    source: String?,
    onFrame: (FrameState) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // What an ambiguous symbol means depends on where the camera is; "$" is a
    // peso in half of Latin America. Read through rememberUpdatedState for the
    // same reason as the engine: the analyser outlives this composition.
    val currentSource = rememberUpdatedState(source)
    // The analyser is built once, inside AndroidView's factory, but the engine
    // is swapped in later when its models finish loading. Reading it through
    // rememberUpdatedState is what keeps the analyser from holding the
    // placeholder for the life of the screen.
    val currentEngine = rememberUpdatedState(engine)
    // A reading has to be seen in several frames before it is shown. Held in an
    // AtomicReference because the analyser runs off the main thread.
    val votes = remember { AtomicReference(VoteState()) }
    // Where each price was last seen. The voting decides *what* to show and
    // keeps a price for a few frames after it goes missing; this remembers
    // where to draw it meanwhile, so a label does not vanish and reappear as
    // the camera wobbles.
    val positions = remember { AtomicReference(emptyMap<Price, converter.core.Box>()) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val seen = remember { AtomicLong(0) }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // The overlay maps the analysed frame onto this view, and that
                // mapping only holds if the whole frame is on screen. The
                // default crops it, which would put a price the camera read
                // outside the picture the reader is looking at.
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    // The default is 640x480, which leaves a price tag across a
                    // room a few pixels tall — and the detector scales its input
                    // to a long side of 960 anyway, so anything less is capacity
                    // thrown away.
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 960),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                )
                            )
                            .build()
                    )
                    .build()

                analysis.setAnalyzer(analysisExecutor) { image ->
                    val started = System.currentTimeMillis()
                    try {
                        val frame = image.toBitmap().upright(image.imageInfo.rotationDegrees)
                        val lines = currentEngine.value.recognize(frame)
                        frame.recycle()
                        val context = PriceContext(pageCurrency = currentSource.value)
                        val located = locatePrices(lines, context)
                        val prices = located.map { it.price }
                        // Prices whose text this frame actually read, as opposed
                        // to carried over from the last one. Only these count
                        // towards confirming a reading.
                        val fresh = locatePrices(lines.filterNot { it.reused }, context)
                            .mapTo(mutableSetOf()) { it.price }
                        val agreed = votes.updateAndGet {
                            it.observe(prices, VoteSettings(), fresh)
                        }

                        val stillTracked = agreed.votes.mapTo(mutableSetOf()) { it.price }
                        val where = positions.updateAndGet { previous ->
                            (previous + located.associate { it.price to it.box })
                                .filterKeys { it in stillTracked }
                        }
                        val confirmed = agreed.confirmed.mapNotNull { price ->
                            where[price]?.let { LocatedPrice(price, it) }
                        }

                        onFrame(
                            FrameState(
                                prices = confirmed,
                                imageWidth = frame.width,
                                imageHeight = frame.height,
                                readLastFrame = prices.size,
                                reusedLastFrame = lines.count { it.reused },
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
private fun ReadingPanel(
    engine: OcrEngine,
    frame: FrameState,
    rates: RateTable?,
    source: String?,
    target: String,
    onChangeSource: () -> Unit,
    onChangeTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        when {
            frame.prices.isEmpty() -> Text(
                text = if (frame.readLastFrame > 0) "Reading\u2026" else "No price in view",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )

            // With rates, the conversions are drawn over the prices themselves;
            // repeating them here would say the same thing twice.
            rates != null -> Text(
                text = if (frame.prices.size == 1) "1 price converted"
                       else "${frame.prices.size} prices converted",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )

            // Without rates there is nothing to draw, so the panel is the only
            // place the reading can appear at all.
            else -> {
                for (located in frame.prices) {
                    Text(
                        text = formatConverted(located.price.amount, located.price.code),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = "No rates yet — showing what was read",
                    color = Color(0xFFFFB74D),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row {
            Setting(
                label = "from ${source ?: "?"}",
                onClick = onChangeSource,
            )
            Text("   ", style = MaterialTheme.typography.bodyMedium)
            Setting(label = "into $target", onClick = onChangeTarget)
        }
        Text(
            text = "${engine.name} · frames: ${frame.framesSeen} · ${frame.lastMillis} ms" +
                if (frame.reusedLastFrame > 0) " · ${frame.reusedLastFrame} reused" else "",
            color = Color(0xFF7A7A7A),
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

/** A tappable currency in the panel. Underlined, so it reads as a control. */
@Composable
private fun Setting(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color(0xFFCFCFCF),
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
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

/**
 * Turns a camera frame the right way up.
 *
 * ImageProxy hands over the sensor's buffer untouched, and a phone's back
 * camera is mounted sideways: held upright, the frame arrives rotated ninety
 * degrees. The recognizer has no model for rotated text, so without this every
 * price in the world reads as nothing — which is exactly what happened, and
 * which no test here could catch, because a test feeds bitmaps that are already
 * upright.
 */
private fun Bitmap.upright(degrees: Int): Bitmap {
    if (degrees % 360 == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated !== this) recycle()
    return rotated
}
