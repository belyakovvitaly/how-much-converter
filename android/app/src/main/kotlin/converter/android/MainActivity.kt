package converter.android

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
import androidx.compose.runtime.setValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color.Black) {
                    val context = LocalContext.current
                    var engine by remember { mutableStateOf<OcrEngine>(UnwiredEngine) }
                    var rates by remember { mutableStateOf<RateTable?>(null) }
                    val currencies = remember { CurrencyStore(context) }
                    val detectedLocal = remember { currencies.detectLocal() }
                    val detectedHome = remember { currencies.detectHome() }
                    var chosenLocal by remember { mutableStateOf(currencies.local) }
                    var chosenHome by remember { mutableStateOf(currencies.home) }
                    var picking by remember { mutableStateOf<Picking?>(null) }
                    var still by remember { mutableStateOf<Still?>(null) }
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
                            val prices = withContext(Dispatchers.Default) {
                                val current = engine
                                current.reset()
                                // Thorough: a still has no next frame to defer
                                // the rest of the reading to.
                                val lines = current.recognize(image, thorough = true)
                                locatePrices(lines, PriceContext(pageCurrency = source))
                            }
                            still = Still.Read(image, prices)
                        }
                    }

                    val fromGallery = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri: Uri? ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        still = Still.Working(null)
                        scope.launch {
                            val image = withContext(Dispatchers.IO) {
                                StillImages.loadUpright(context, uri)
                            }
                            read(image)
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
                            fromGallery.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    )

                    still?.let { current ->
                        StillScreen(
                            still = current,
                            rates = rates,
                            target = target,
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

    /** Which of the two currencies the picker is open for. */
    private enum class Picking { Source, Target }

    private companion object {
        const val TAG = "HowMuch"
    }
}
