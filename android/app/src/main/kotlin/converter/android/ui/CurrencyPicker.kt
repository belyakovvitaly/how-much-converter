package converter.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row as LayoutRow
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import converter.core.CURRENCY_CODES
import converter.core.CURRENCY_NAMES
import converter.core.flagFor

/**
 * Picks one of the two currencies.
 *
 * "Automatic" is first and is the default; the detected currency is named
 * beside it so the choice is informed rather than blind. Detection can come to
 * nothing for the local currency — a phone with no network knows where it is
 * from but not where it is — and the row says so rather than pretending.
 *
 * The other side's currency is not in the list: converting a currency into
 * itself answers nothing. If detection would land on it, "Automatic" says so
 * and cannot be picked.
 */
@Composable
fun CurrencyPicker(
    title: String,
    current: String?,
    automatic: Boolean,
    detected: String?,
    automaticSubtitle: String,
    /** The other side's currency, which this one may not also be. */
    excluded: String?,
    /** What the other side is, for saying why a row is not offered. */
    excludedRole: String,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    val blocked = detected != null && detected == excluded
                    Row(
                        title = "Automatic",
                        subtitle = automaticSubtitle + when {
                            detected == null -> " — could not tell"
                            blocked -> " — $detected, already $excludedRole"
                            else -> " — $detected"
                        },
                        selected = automatic,
                        onClick = { onPick(null) },
                        flag = detected?.let { flagFor(it) },
                        enabled = !blocked,
                    )
                }
                items(CURRENCY_CODES.filter { it != excluded }) { code ->
                    Row(
                        title = code,
                        subtitle = CURRENCY_NAMES[code].orEmpty(),
                        selected = !automatic && code == current,
                        onClick = { onPick(code) },
                        flag = flagFor(code),
                    )
                }
            }
        },
    )
}

@Composable
private fun Row(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    flag: String? = null,
    enabled: Boolean = true,
) {
    LayoutRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A fixed width whether or not there is a flag, so the codes line up
        // down the list rather than stepping in and out.
        Box(Modifier.width(44.dp)) {
            if (flag != null) {
                Text(text = flag, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Column {
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
}
