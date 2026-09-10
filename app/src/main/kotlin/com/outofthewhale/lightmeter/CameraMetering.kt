package com.outofthewhale.lightmeter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

/**
 * Runs the meter for as long as the app is in front, with nothing to show for
 * itself on screen. There is no viewfinder: you point the phone and read the
 * number, the way you would a handheld meter.
 *
 * Metering is bound to the RESUMED state rather than to the composition, so
 * leaving the app hands the camera straight back rather than holding it open
 * behind whatever you switched to.
 */
@Composable
fun CameraMetering(
    lens: Lens,
    locked: Boolean,
    onState: (MeterState) -> Unit,
) {
    val context = LocalContext.current
    val meter = remember { CameraMeter(context.applicationContext) }
    val latestOnState = rememberUpdatedState(onState)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(meter) {
        meter.state.collect { latestOnState.value(it) }
    }

    LaunchedEffect(meter, lens, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            meter.start(lens)
            try {
                awaitCancellation()
            } finally {
                meter.stop()
            }
        }
    }

    LaunchedEffect(meter, locked) {
        meter.setLocked(locked)
    }
}
