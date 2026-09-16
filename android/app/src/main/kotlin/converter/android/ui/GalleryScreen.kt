package converter.android.ui

import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.photopicker.compose.EmbeddedPhotoPicker
import androidx.photopicker.compose.ExperimentalPhotoPickerComposeApi
import androidx.photopicker.compose.rememberEmbeddedPhotoPickerState

/**
 * Whether the system can show its photo picker inside this app's own screen.
 *
 * It needs Android 14 with the SDK extension that added it. Where it is
 * missing, the gallery button opens the system's picker as its own activity
 * instead — which, when Google Photos is part of it, asks for Done after the
 * tap.
 */
fun embeddedPickerAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 15

/**
 * The system's photo picker, hosted in this screen, so that one tap opens a
 * picture.
 *
 * The standalone picker, as Android 16 shows it with Google Photos, treats even
 * a single choice as a selection to confirm, and asks for Done. Embedded, the
 * picker reports each choice the moment it is made, and the first one is the
 * answer. It is still the system's picker, drawn by the system: the app sees
 * only the picture it was given, and needs no permission to see even that.
 *
 * [onUnavailable] is called if the picker cannot start after all, so the caller
 * can fall back to the standalone one rather than leave an empty screen.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 15)
@OptIn(ExperimentalPhotoPickerComposeApi::class)
@Composable
fun GalleryScreen(
    onPicked: (Uri) -> Unit,
    onUnavailable: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    val state = rememberEmbeddedPhotoPickerState(
        initialExpandedValue = true,
        onSessionError = { onUnavailable() },
        onUriPermissionGranted = { uris -> uris.firstOrNull()?.let(onPicked) },
    )
    val features = remember {
        EmbeddedPhotoPickerFeatureInfo.Builder()
            .setMaxSelectionLimit(1)
            .setMimeTypes(listOf("image/*"))
            .setThemeNightMode(Configuration.UI_MODE_NIGHT_YES)
            .build()
    }

    Column(modifier.fillMaxSize().background(Color.Black)) {
        // Above the picker, not over it: the picker draws on top of the window,
        // and anything laid across it would be hidden.
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onClose)
            Text(
                text = "Choose a photo",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            EmbeddedPhotoPicker(
                state = state,
                embeddedPhotoPickerFeatureInfo = features,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
