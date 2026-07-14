package dev.code93.colombian_id_reader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** ID-1 card aspect ratio (ISO/IEC 7810): 85.6mm x 54mm. */
private const val CARD_ASPECT = 54f / 85.6f
private const val WINDOW_WIDTH_FRACTION = 0.85f

/**
 * Dark scrim with a transparent card-shaped window as a framing guide.
 * The window is a guide only — frames are analyzed uncropped.
 */
@Composable
internal fun ScannerOverlay(
    instruction: String,
    cancelLabel: String,
    onCancel: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth * WINDOW_WIDTH_FRACTION
        val windowHeight = windowWidth * CARD_ASPECT

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.6f))
            val cutout = Size(windowWidth.toPx(), windowHeight.toPx())
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(
                    (size.width - cutout.width) / 2f,
                    (size.height - cutout.height) / 2f
                ),
                size = cutout,
                cornerRadius = CornerRadius(16.dp.toPx()),
                blendMode = BlendMode.Clear
            )
        }

        Text(
            text = instruction,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = windowHeight / 2 + 32.dp)
                .padding(horizontal = 24.dp)
        )

        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Text(cancelLabel, color = Color.White)
        }
    }
}
