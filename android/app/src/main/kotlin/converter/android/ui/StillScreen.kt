package converter.android.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import converter.core.LocatedPrice
import converter.core.RateTable

/** A still photograph, with its prices converted in place. */
sealed interface Still {
    /** Loading or reading; the picture may already be there to look at. */
    data class Working(val image: Bitmap?) : Still

    data class Read(val image: Bitmap, val prices: List<LocatedPrice>) : Still

    data class Failed(val reason: String) : Still
}

/**
 * Shows a photograph with the conversions drawn on it.
 *
 * The same overlay as the live camera, over a picture that holds still. A still
 * is read thoroughly rather than within a frame's budget — there is no next
 * frame to defer to, and no reason to hurry.
 *
 * Back — the arrow, or the system's gesture — returns to the camera. Only the
 * camera itself lets back close the app.
 */
@Composable
fun StillScreen(
    still: Still,
    rates: RateTable?,
    target: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    Box(modifier.fillMaxSize().background(Color.Black)) {
        val image = when (still) {
            is Still.Working -> still.image
            is Still.Read -> still.image
            is Still.Failed -> null
        }

        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // Fit, not crop: the overlay maps the whole picture onto this
                // view, and a cropped edge would put a label off-screen.
                contentScale = ContentScale.Fit,
            )
        }

        if (still is Still.Read) {
            PriceOverlay(
                prices = still.prices,
                imageWidth = still.image.width,
                imageHeight = still.image.height,
                rates = rates,
                target = target,
            )
        }

        if (still is Still.Working) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = when (still) {
                    is Still.Working -> "Reading…"
                    is Still.Failed -> still.reason
                    is Still.Read -> when {
                        still.prices.isEmpty() -> "No price found"
                        still.prices.size == 1 -> "1 price converted"
                        else -> "${still.prices.size} prices converted"
                    }
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        BackButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start)
                )
                .padding(8.dp),
        )
    }
}
