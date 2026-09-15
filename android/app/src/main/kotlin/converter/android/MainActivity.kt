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
import converter.android.ui.CameraScreen
import converter.core.CURRENCY_CODES
import converter.core.RateTable
import converter.core.RatesOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Currency
import java.util.Locale

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
                    val target = remember { defaultTargetCurrency() }

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

                    CameraScreen(engine = engine, rates = rates, target = target)
                }
            }
        }
    }

    private companion object {
        const val TAG = "HowMuch"

        /**
         * What to convert into, until there is a setting for it: the currency of
         * the device's own locale, which is the one its owner thinks in.
         */
        fun defaultTargetCurrency(): String {
            val fromLocale = runCatching {
                Currency.getInstance(Locale.getDefault()).currencyCode
            }.getOrNull()
            return fromLocale?.takeIf { it in CURRENCY_CODES } ?: "USD"
        }
    }
}
