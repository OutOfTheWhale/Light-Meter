package com.outofthewhale.lightmeter

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/** The viewfinder. Aiming aid only - the reading comes from metadata, not pixels. */
@Composable
fun CameraPreview(
    lens: Lens,
    locked: Boolean,
    onState: (MeterState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val meter = remember { CameraMeter(context) }
    var texture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var view by remember { mutableStateOf<TextureView?>(null) }
    var geometry by remember { mutableStateOf<PreviewGeometry?>(null) }
    val latestOnState = rememberUpdatedState(onState)

    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { factoryContext ->
            TextureView(factoryContext).also { textureView ->
                view = textureView
                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        texture = surface
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        texture = null
                        meter.stop()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
    )

    // Opening waits on both the surface and the chosen lens, and starts again
    // whenever the lens is switched.
    LaunchedEffect(texture, lens) {
        val surface = texture ?: return@LaunchedEffect
        meter.start(surface, lens)
    }

    LaunchedEffect(locked) { meter.setLocked(locked) }

    LaunchedEffect(meter) {
        meter.state.collect { latestOnState.value(it) }
    }

    LaunchedEffect(meter) {
        meter.geometry.collect { geometry = it }
    }

    LaunchedEffect(geometry, view) {
        val target = view ?: return@LaunchedEffect
        val shape = geometry ?: return@LaunchedEffect
        target.post { applyPreviewTransform(target, shape) }
    }

    DisposableEffect(meter) {
        onDispose { meter.stop() }
    }
}

/**
 * TextureView stretches its buffer across the whole view. Undo that, rotate the
 * frame upright, then scale it up until it covers - the same centre-crop a
 * camera app does, so what you see is what you are metering.
 */
internal fun applyPreviewTransform(view: TextureView, geometry: PreviewGeometry) {
    val viewWidth = view.width.toFloat()
    val viewHeight = view.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f) return

    val displayRotation = when (view.display?.rotation ?: 0) {
        1 -> 90
        2 -> 180
        3 -> 270
        else -> 0
    }
    val rotation = if (geometry.front) {
        (geometry.sensorOrientation + displayRotation) % 360
    } else {
        (geometry.sensorOrientation - displayRotation + 360) % 360
    }
    val quarterTurned = rotation == 90 || rotation == 270

    val centerX = viewWidth / 2f
    val centerY = viewHeight / 2f
    val matrix = Matrix()

    matrix.postRotate(rotation.toFloat(), centerX, centerY)

    // A quarter turn leaves the frame lying across the view; stretch it back to
    // the view's bounds before correcting the aspect.
    if (quarterTurned) {
        matrix.postScale(viewWidth / viewHeight, viewHeight / viewWidth, centerX, centerY)
    }

    val contentWidth = if (quarterTurned) geometry.bufferHeight else geometry.bufferWidth
    val contentHeight = if (quarterTurned) geometry.bufferWidth else geometry.bufferHeight
    val contentAspect = contentWidth.toFloat() / contentHeight
    val viewAspect = viewWidth / viewHeight
    if (contentAspect > viewAspect) {
        matrix.postScale(contentAspect / viewAspect, 1f, centerX, centerY)
    } else {
        matrix.postScale(1f, viewAspect / contentAspect, centerX, centerY)
    }

    // A selfie preview that is not mirrored feels broken to point.
    if (geometry.front) matrix.postScale(-1f, 1f, centerX, centerY)

    view.setTransform(matrix)
}
