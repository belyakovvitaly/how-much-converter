package converter.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** Where the rates come from. */
const val RATES_SOURCE_URL = "https://www.exchangerate-api.com"

/**
 * Credit to the rate service, linked, wherever its rates are shown.
 *
 * Not a courtesy: the service's free access asks for this link on every page
 * that uses its rates, in these words, and may cut off access without it.
 */
@Composable
fun RatesAttribution(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Text(
        text = "Rates By Exchange Rate API",
        color = Color(0xFF9E9E9E),
        style = MaterialTheme.typography.bodySmall,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .clickable { uri.openUri(RATES_SOURCE_URL) }
            .padding(vertical = 4.dp),
    )
}
