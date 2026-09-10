package com.outofthewhale.lightmeter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val RowHeight: Dp = 40.dp

/**
 * A detented scroll wheel: three rows tall, the middle one selected, hairlines
 * marking the detent. Scrolling reports the centred row as it passes, so the
 * readout above moves with your thumb rather than waiting for the wheel to
 * settle.
 */
@Composable
fun <T> WheelPicker(
    title: String,
    items: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }
    val colors = LightTokens.colors

    LaunchedEffect(state, items.size) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val centred = if (offset > rowHeightPx / 2) index + 1 else index
                val clamped = centred.coerceIn(items.indices)
                if (clamped != selectedIndex) onSelected(clamped)
            }
    }

    Column(modifier = modifier) {
        LightText(
            text = title,
            variant = LightVariant.Micro,
            lighten = true,
            align = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight * 3),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val thickness = 1.dp.toPx()
                listOf(size.height / 3f, size.height * 2f / 3f).forEach { y ->
                    drawLine(
                        color = colors.contentSecondary,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = thickness,
                    )
                }
            }
            LazyColumn(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state),
                // One blank row's worth at each end, so the first and last
                // entries can still reach the middle.
                contentPadding = PaddingValues(vertical = RowHeight),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items) { index, item ->
                    val centred = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RowHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        LightText(
                            text = label(item),
                            variant = if (centred) LightVariant.Heading else LightVariant.Detail,
                            lighten = !centred,
                            align = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
