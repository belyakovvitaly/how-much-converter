package converter.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Where the app starts: the two currencies, and the two ways to get an amount
 * into them — typed into the calculator, or read from a picture, taken now or
 * chosen from the gallery.
 */
@Composable
fun HomeScreen(
    source: String?,
    target: String?,
    onChangeSource: () -> Unit,
    onChangeTarget: () -> Unit,
    onCalculator: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "How Much?",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(12.dp))
            CurrencyBar(source, target, onChangeSource, onChangeTarget)
            Text(
                text = when {
                    source == null -> "Choose what the prices are in"
                    target == null -> "Choose what to convert into"
                    else -> "Tap a currency to change it"
                },
                color = if (source == null || target == null) Color(0xFFFFB74D) else Color(0xFF757575),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))

            Choice(
                title = "Calculator",
                subtitle = "Type an amount",
                onClick = onCalculator,
                modifier = Modifier.fillMaxWidth(),
            ) { CalculatorIcon(it) }

            Text(
                text = "From a picture",
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Choice(
                    title = "Camera",
                    subtitle = "Take a photo",
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                ) { CameraIcon(it) }
                Choice(
                    title = "Gallery",
                    subtitle = "Choose a photo",
                    onClick = onGallery,
                    modifier = Modifier.weight(1f),
                ) { GalleryIcon(it) }
            }
            Spacer(Modifier.height(24.dp))
        }

        VersionLabel(Modifier.align(Alignment.TopEnd))
    }
}

/** One way in: a large tile with a drawn icon, what it is, and what it does. */
@Composable
private fun Choice(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(Modifier.size(56.dp))
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = subtitle,
            color = Color(0xFF9E9E9E),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
