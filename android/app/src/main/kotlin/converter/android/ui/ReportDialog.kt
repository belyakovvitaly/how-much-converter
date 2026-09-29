package converter.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Asks what went wrong before a report is shared.
 *
 * Says plainly what the report holds, since the picture goes with it and a
 * receipt can carry a card's digits or a name: nothing leaves until the reader
 * picks where it goes, but they should know what they are sending.
 */
@Composable
fun ReportDialog(
    /** Whether the report goes to the developer's mail rather than wherever the reader picks. */
    byEmail: Boolean,
    onSend: (note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report a problem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What was wrong?") },
                    placeholder = { Text("e.g. the price on the left was not read") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "The report holds this photo, what the app read from it, " +
                        "and the currencies. " +
                        (if (byEmail) "It opens in your mail app, addressed to the developer, " +
                            "and is sent only when you send it there. "
                         else "You choose where to send it. ") +
                        "A receipt may show private details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(note) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
