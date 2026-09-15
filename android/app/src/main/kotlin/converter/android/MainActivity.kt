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
import converter.android.rates.TargetCurrencyStore
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
                    val targets = remember { TargetCurrencyStore(context) }
                    val detected = remember { targets.detect() }
                    var chosen by remember { mutableStateOf(targets.chosen) }
                    var picking by remember { mutableStateOf(false) }
                    val target = chosen ?: detected

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
                        target = target,
                        onChangeTarget = { picking = true },
                    )

                    if (picking) {
                        CurrencyPicker(
                            current = target,
                            automatic = chosen == null,
                            detected = detected,
                            onPick = { code ->
                                targets.chosen = code
                                chosen = code
                                picking = false
                            },
                            onDismiss = { picking = false },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "HowMuch"
    }
}
