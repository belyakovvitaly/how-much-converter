package converter.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import converter.android.ocr.UnwiredEngine
import converter.android.ui.CameraScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color.Black) {
                    // The recognizer is the only piece still missing; the rules
                    // it feeds are already under test in :core.
                    CameraScreen(engine = UnwiredEngine)
                }
            }
        }
    }
}
