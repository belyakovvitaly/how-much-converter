package converter.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// The two controls are drawn rather than imported. Material's extended icon set
// is several megabytes for the sake of two glyphs, and these two are a circle
// and a picture frame.

/** The shutter: a white disc inside a thin ring, as every camera app has. */
@Composable
fun ShutterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        val centre = Offset(size.width / 2, size.height / 2)
        val outer = size.minDimension / 2
        drawCircle(
            color = Color.White,
            radius = outer - 2.dp.toPx(),
            center = centre,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawCircle(color = Color.White, radius = outer - 9.dp.toPx(), center = centre)
    }
}

/** A picture: the frame, a sun in one corner, a hill across the bottom. */
@Composable
fun GalleryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        val stroke = 2.dp.toPx()
        val inset = size.minDimension * 0.24f
        val side = size.minDimension - inset * 2

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(inset, inset),
            size = Size(side, side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.16f),
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = Color.White,
            radius = side * 0.1f,
            center = Offset(inset + side * 0.3f, inset + side * 0.3f),
        )

        // The hill, clipped to the frame by construction: it starts and ends on
        // the frame's own bottom edge.
        val bottom = inset + side - stroke / 2
        val hill = Path().apply {
            moveTo(inset + stroke / 2, bottom)
            lineTo(inset + side * 0.42f, inset + side * 0.5f)
            lineTo(inset + side * 0.68f, inset + side * 0.74f)
            lineTo(inset + side * 0.82f, inset + side * 0.6f)
            lineTo(inset + side - stroke / 2, bottom)
        }
        drawPath(hill, color = Color.White, style = Stroke(width = stroke))
    }
}
