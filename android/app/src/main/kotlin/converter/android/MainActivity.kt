package converter.android

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import converter.android.ocr.OcrEngine
import converter.android.ocr.PaddleOnnxEngine
import converter.android.ocr.StillImages
import converter.android.ocr.UnwiredEngine
import converter.android.rates.RatesRepository
import converter.android.rates.CurrencyStore
import converter.android.ui.CameraScreen
import converter.android.ui.CurrencyPicker
import converter.android.ui.GalleryScreen
import converter.android.ui.embeddedPickerAvailable
import converter.android.ui.Still
import converter.android.ui.StillScreen
import converter.core.RateTable
import converter.core.PriceContext
import converter.core.RatesOutcome
import converter.core.locatePrices
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /**
     * A picture another app shared with this one, waiting to be opened.
     *
     * Only taken from the intent that started the activity, not from a saved
     * state, so coming back to the app does not open the same picture again.
     */
    private var shared by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) shared = sharedImage(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color.Black) {
                    val context = LocalContext.current
                    var engine by remember { mutableStateOf<OcrEngine>(UnwiredEngine) }
                    // Whether loading the models has finished, either way.
                    var engineSettled by remember { mutableStateOf(false) }
                    var rates by remember { mutableStateOf<RateTable?>(null) }
                    val currencies = remember { CurrencyStore(context) }
                    val detectedLocal = remember { currencies.detectLocal() }
                    val detectedHome = remember { currencies.detectHome() }
                    var chosenLocal by remember { mutableStateOf(currencies.local) }
                    var chosenHome by remember { mutableStateOf(currencies.home) }
                    var picking by remember { mutableStateOf<Picking?>(null) }
                    var still by remember { mutableStateOf<Still?>(null) }
                    var receipt by rememberSaveable { mutableStateOf(false) }
                    var browsing by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    // What the prices are in, and what to turn them into. Not
                    // the same question: abroad, the first is the country's and
                    // the second is the reader's.
                    val source = chosenLocal ?: detectedLocal
                    val target = chosenHome ?: detectedHome

                    // Reading two ONNX models out of assets costs a second or
                    // more; the camera starts without waiting for it, and the
                    // panel says which engine is running meanwhile.
                    LaunchedEffect(Unit) {
                        engine = withContext(Dispatchers.IO) {
                            runCatching { PaddleOnnxEngine.create(context) }
                                .onFailure { Log.e(TAG, "could not load the OCR models", it) }
                                .getOrDefault(UnwiredEngine)
                        }
                        engineSettled = true
                    }

                    // Rates load in parallel. Prices are shown as read until
                    // they arrive, rather than held back behind the network.
                    LaunchedEffect(Unit) {
                        rates = withContext(Dispatchers.IO) {
                            when (val outcome = RatesRepository(context).load()) {
                                is RatesOutcome.Fresh -> outcome.table
                                is RatesOutcome.Stale -> outcome.table
                                is RatesOutcome.Unavailable -> {
                                    Log.w(TAG, "no rates: ${outcome.reason}")
                                    null
                                }
                            }
                        }
                    }

                    // Reading a still photograph, in one place, so the shutter
                    // and the gallery cannot drift apart.
                    fun read(image: Bitmap?) {
                        if (image == null) {
                            still = Still.Failed("Could not open that picture")
                            return
                        }
                        still = Still.Working(image)
                        scope.launch {
                            // A picture can arrive before the models have
                            // loaded — shared from another app, most often.
                            // Reading it with the placeholder would report no
                            // price in a picture full of them.
                            snapshotFlow { engineSettled }.first { it }
                            if (!engine.ready) {
                                still = Still.Failed("The recognizer could not be loaded")
                                return@launch
                            }
                            val lines = withContext(Dispatchers.Default) {
                                val current = engine
                                current.reset()
                                // Thorough: a still has no next frame to defer
                                // the rest of the reading to.
                                current.recognize(image, thorough = true)
                            }
                            still = Still.Read(image, lines)
                        }
                    }

                    fun open(uri: Uri) {
                        still = Still.Working(null)
                        scope.launch {
                            val image = withContext(Dispatchers.IO) {
                                StillImages.loadUpright(context, uri)
                            }
                            read(image)
                        }
                    }

                    // The standalone picker, for where the embedded one is not
                    // available.
                    val fromGallery = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri: Uri? -> uri?.let(::open) }

                    fun pickStandalone() = fromGallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )

                    // A picture shared from another app opens as if it had
                    // been picked from the gallery.
                    LaunchedEffect(shared) {
                        shared?.let { uri ->
                            shared = null
                            browsing = false
                            open(uri)
                        }
                    }

                    CameraScreen(
                        engine = engine,
                        rates = rates,
                        source = source,
                        target = target,
                        onChangeSource = { picking = Picking.Source },
                        onChangeTarget = { picking = Picking.Target },
                        onPhoto = { read(it) },
                        onPickFromGallery = {
                            if (embeddedPickerAvailable()) browsing = true else pickStandalone()
                        },
                    )

                    if (browsing && embeddedPickerAvailable()) {
                        GalleryScreen(
                            onPicked = { uri ->
                                browsing = false
                                open(uri)
                            },
                            onUnavailable = {
                                Log.w(TAG, "embedded photo picker failed; using the standalone one")
                                browsing = false
                                pickStandalone()
                            },
                            onClose = { browsing = false },
                        )
                    }

                    still?.let { current ->
                        // Worked out again when a currency or the receipt
                        // switch changes; the reading itself is kept.
                        val lines = (current as? Still.Read)?.lines.orEmpty()
                        val prices = remember(lines, source, receipt) {
                            locatePrices(
                                lines,
                                PriceContext(
                                    pageCurrency = source,
                                    bareAmounts = if (receipt) source else null,
                                ),
                            )
                        }
                        StillScreen(
                            still = current,
                            prices = prices,
                            rates = rates,
                            source = source,
                            target = target,
                            receipt = receipt,
                            onReceiptChange = { on ->
                                receipt = on
                                // A receipt names no currency, so there is
                                // nothing to read its amounts in until one is
                                // chosen.
                                if (on && source == null) picking = Picking.Source
                            },
                            onChangeSource = { picking = Picking.Source },
                            onChangeTarget = { picking = Picking.Target },
                            onClose = { still = null },
                        )
                    }

                    when (picking) {
                        Picking.Source -> CurrencyPicker(
                            title = "Prices are in",
                            current = source,
                            automatic = chosenLocal == null,
                            detected = detectedLocal,
                            automaticSubtitle = "where the phone is",
                            onPick = { code ->
                                currencies.local = code
                                chosenLocal = code
                                picking = null
                            },
                            onDismiss = { picking = null },
                        )

                        Picking.Target -> CurrencyPicker(
                            title = "Convert into",
                            current = target,
                            automatic = chosenHome == null,
                            detected = detectedHome,
                            automaticSubtitle = "where the phone is from",
                            onPick = { code ->
                                currencies.home = code
                                chosenHome = code
                                picking = null
                            },
                            onDismiss = { picking = null },
                        )

                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedImage(intent)?.let { shared = it }
    }

    /** The image in a share, if this intent is one. */
    private fun sharedImage(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }

    /** Which of the two currencies the picker is open for. */
    private enum class Picking { Source, Target }

    private companion object {
        const val TAG = "HowMuch"
    }
}
