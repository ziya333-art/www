package com.jegly.www.presentation.lock

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.hypot

/**
 * 3x3 unlock pattern.
 *
 * Dots are indexed 0..8 left-to-right, top-to-bottom, and the drawn order is the secret — the same
 * dots in a different order are a different pattern. A dot is captured when the finger comes within
 * [HIT_RADIUS_FRACTION] of its centre; there is no "pass-through" auto-selection of intermediate
 * dots, so what the user sees selected is exactly what is recorded.
 */
@Composable
fun PatternGrid(
    onComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dotColor: Color = Color.Gray,
    activeColor: Color = Color.Cyan
) {
    val selected = remember { mutableStateListOf<Int>() }
    var currentTouch by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = modifier.size(260.dp)) {
        Canvas(
            modifier = Modifier
                .size(260.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            selected.clear()
                            currentTouch = offset
                            hitDot(offset, size.width.toFloat())?.let { selected.add(it) }
                        },
                        onDrag = { change, _ ->
                            currentTouch = change.position
                            hitDot(change.position, size.width.toFloat())?.let { dot ->
                                if (dot !in selected) selected.add(dot)
                            }
                        },
                        onDragEnd = {
                            currentTouch = null
                            if (selected.isNotEmpty()) onComplete(selected.toList())
                        },
                        onDragCancel = {
                            currentTouch = null
                            selected.clear()
                        }
                    )
                }
        ) {
            val spacing = size.width / 3f
            fun centre(index: Int) = Offset(
                x = spacing * (index % 3) + spacing / 2f,
                y = spacing * (index / 3) + spacing / 2f
            )

            // Connecting lines, in selection order.
            for (i in 0 until selected.size - 1) {
                drawLine(
                    color = activeColor,
                    start = centre(selected[i]),
                    end = centre(selected[i + 1]),
                    strokeWidth = 8f
                )
            }
            // Trailing segment to the finger.
            val touch = currentTouch
            if (touch != null && selected.isNotEmpty()) {
                drawLine(
                    color = activeColor,
                    start = centre(selected.last()),
                    end = touch,
                    strokeWidth = 8f
                )
            }

            repeat(9) { index ->
                val isOn = index in selected
                drawCircle(
                    color = if (isOn) activeColor else dotColor,
                    radius = if (isOn) spacing * 0.16f else spacing * 0.10f,
                    center = centre(index)
                )
                if (isOn) {
                    drawCircle(
                        color = activeColor,
                        radius = spacing * 0.28f,
                        center = centre(index),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}

private const val HIT_RADIUS_FRACTION = 0.22f

/** Index of the dot under [point], or null if the touch isn't close enough to any. */
private fun hitDot(point: Offset, canvasWidth: Float): Int? {
    val spacing = canvasWidth / 3f
    val threshold = spacing * HIT_RADIUS_FRACTION * 2f
    repeat(9) { index ->
        val cx = spacing * (index % 3) + spacing / 2f
        val cy = spacing * (index / 3) + spacing / 2f
        if (hypot(point.x - cx, point.y - cy) < threshold) return index
    }
    return null
}
