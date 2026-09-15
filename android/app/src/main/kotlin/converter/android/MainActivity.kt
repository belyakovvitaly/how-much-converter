package converter.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import converter.android.ocr.UnwiredEngine
import converter.android.rates.RatesRepository
import converter.android.rates.CurrencyStore
import converter.android.ui.CameraScreen
import converter.android.ui.CurrencyPicker
import converter.core.RateTable
import converter.core.RatesOutcome
import kotlinx.coroutines.Dispatchers
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

                    CameraScreen(
                        engine = engine,
                        rates = rates,
                        source = source,
                        target = target,
                        onChangeSource = { picking = Picking.Source },
                        onChangeTarget = { picking = Picking.Target },
                    )

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
