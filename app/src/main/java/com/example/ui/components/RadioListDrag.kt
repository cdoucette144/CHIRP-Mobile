package com.example.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt

internal class RadioListDrag(private val list: LazyListState) {
    private var source by mutableIntStateOf(-1)
    private var target by mutableIntStateOf(-1)
    private var distance by mutableFloatStateOf(0f)

    fun row(index: Int, indicator: Color): Modifier {
        val lifted = source == index
        return Modifier
            .zIndex(if (lifted) 2f else 0f)
            .offset { IntOffset(0, if (lifted) distance.roundToInt() else 0) }
            .alpha(if (lifted) 0.7f else 1f)
            .drawWithContent {
                drawContent()
                if (index == target && source != target && source >= 0) {
                    val y = if (target > source) size.height - 2.dp.toPx() else 2.dp.toPx()
                    drawLine(indicator, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
                }
            }
    }

    fun handle(index: Int, range: IntRange, move: () -> (Int) -> Unit): Modifier =
        Modifier.pointerInput(this, index, range) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    source = index
                    target = index
                    distance = 0f
                },
                onDrag = { change, delta ->
                    change.consume()
                    distance += delta.y
                    val visible = list.layoutInfo.visibleItemsInfo
                    val item = visible.firstOrNull { it.index == index }
                    if (item != null) {
                        val center = item.offset + item.size / 2f + distance
                        target = visible.filter { it.index in range }
                            .minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: index
                    }
                },
                onDragEnd = {
                    val destination = target
                    reset()
                    if (destination in range && destination != index) move()(destination)
                },
                onDragCancel = { reset() }
            )
        }

    private fun reset() {
        source = -1
        target = -1
        distance = 0f
    }
}
