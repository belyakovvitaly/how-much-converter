package converter.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import converter.core.CURRENCY_CODES
import converter.core.CURRENCY_NAMES

/**
 * Picks what to convert into.
 *
 * "Automatic" is first and is the default, because the right answer is usually
 * where the reader is standing; the detected currency is named beside it so the
 * choice is informed rather than blind.
 */
@Composable
fun CurrencyPicker(
    current: String,
    automatic: Boolean,
    detected: String,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert into") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    Row(
                        title = "Automatic",
                        subtitle = "where the phone is — $detected",
                        selected = automatic,
                        onClick = { onPick(null) },
                    )
                }
                items(CURRENCY_CODES) { code ->
                    Row(
                        title = code,
                        subtitle = CURRENCY_NAMES[code].orEmpty(),
                        selected = !automatic && code == current,
                        onClick = { onPick(code) },
                    )
                }
            }
        },
    )
}

@Composable
private fun Row(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
