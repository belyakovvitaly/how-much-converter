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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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

/** What the frames so far agree on. */
data class FrameState(
    /** Confirmed by several frames, not just read once — see [VoteState]. */
    val prices: List<LocatedPrice> = emptyList(),
    /** The analysed frame's size, which the overlay maps onto the view. */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    /** How many readings the last frame produced, confirmed or not. */
    val readLastFrame: Int = 0,
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
    /** A photograph taken here, already the right way up. */
    onPhoto: (Bitmap) -> Unit = {},
    onPickFromGallery: () -> Unit = {},
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
            val capture = remember { ImageCapture.Builder().build() }
            CameraPreview(engine, source, capture) { frame = it }
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
                onPhoto = { takePhoto(capture, context, onPhoto) },
                onPickFromGallery = onPickFromGallery,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            PermissionPrompt(
                onAsk = { ask.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        VersionLabel(Modifier.align(Alignment.TopEnd))
    }
}

/**
 * Which build this is, small and out of the way, so a report from a shop can
 * say which version it was about.
 */
@Composable
private fun VersionLabel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
    } ?: return
    Text(
        text = "v$version",
        color = Color.White.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun CameraPreview(
    engine: OcrEngine,
    source: String?,
    capture: ImageCapture,
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
                    capture,
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
    onPhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            // The window is edge to edge, so the panel has to clear the gesture
            // bar itself; the background stays behind it, the text does not.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Status(frame = frame, rates = rates, engine = engine)
        CurrencyBar(
            source = source,
            target = target,
            onChangeSource = onChangeSource,
            onChangeTarget = onChangeTarget,
        )
        Box(Modifier.fillMaxWidth()) {
            GalleryButton(onPickFromGallery, Modifier.align(Alignment.CenterStart))
            ShutterButton(onPhoto, Modifier.align(Alignment.Center))
        }
    }
}

/**
 * What the app is converting, and into what.
 *
 * The two currencies are the only settings there are, and the arrow between
 * them says which way round they go — which is the thing that has to be obvious
 * at a glance, since reading it backwards converts the local currency into
 * itself.
 */
@Composable
internal fun CurrencyBar(
    source: String?,
    target: String,
    onChangeSource: () -> Unit,
    onChangeTarget: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CurrencyButton(source ?: "?", onChangeSource)
        Text(
            text = "\u2192",
            color = Color(0xFF9E9E9E),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        CurrencyButton(target, onChangeTarget)
    }
}

@Composable
private fun CurrencyButton(code: String, onClick: () -> Unit) {
    Text(
        text = code,
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** One quiet line: what came of the last look, and nothing about the machinery. */
@Composable
private fun Status(frame: FrameState, rates: RateTable?, engine: OcrEngine) {
    when {
        // Until the models are in memory there is no engine, and reporting "no
        // price in view" about a price plainly in view would be a small lie.
        !engine.ready -> Text(
            text = "Starting\u2026",
            color = Color(0xFFBDBDBD),
            style = MaterialTheme.typography.bodyLarge,
        )

        frame.prices.isEmpty() -> Text(
            text = if (frame.readLastFrame > 0) "Reading\u2026" else "No price in view",
            color = Color(0xFFBDBDBD),
            style = MaterialTheme.typography.bodyLarge,
        )

        // With rates, the conversions are drawn over the prices themselves;
        // repeating them here would say the same thing twice.
        rates != null -> Text(
            text = if (frame.prices.size == 1) "1 price converted"
                   else "${frame.prices.size} prices converted",
            color = Color(0xFFBDBDBD),
            style = MaterialTheme.typography.bodyLarge,
        )

        // Without rates there is nothing to draw, so this is the only place the
        // reading can appear at all.
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

    if (engine.ready && !engine.readsCyrillic) {
        Text(
            // Not a disclaimer for its own sake: a Latin-only recognizer
            // corrupts Cyrillic prices rather than missing them.
            text = "This engine cannot read Cyrillic prices",
            color = Color(0xFFFFB74D),
            style = MaterialTheme.typography.bodySmall,
        )
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

/**
 * Takes a still and hands it over upright.
 *
 * The same rotation the analyser needs: a captured frame carries the sensor's
 * orientation rather than the phone's, and text lying on its side reads as
 * nothing.
 */
private fun takePhoto(
    capture: ImageCapture,
    context: android.content.Context,
    onPhoto: (Bitmap) -> Unit,
) {
    capture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val upright = try {
                    image.toBitmap().upright(image.imageInfo.rotationDegrees)
                } finally {
                    image.close()
                }
                onPhoto(upright)
            }

            override fun onError(exception: ImageCaptureException) {
                android.util.Log.e("HowMuch", "could not take a photograph", exception)
            }
        },
    )
}
