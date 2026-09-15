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
import converter.android.ui.CameraScreen
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

                    CameraScreen(engine = engine)
                }
            }
        }
    }

    private companion object {
        const val TAG = "HowMuch"
    }
}
