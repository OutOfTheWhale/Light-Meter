package com.outofthewhale.lightmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun MeterScreen(viewModel: MeterViewModel, cameraGranted: Boolean) {
    val settings by viewModel.settings.collectAsState()
    val meterState by viewModel.meterState.collectAsState()
    val held by viewModel.held.collectAsState()
    val calibrating by viewModel.calibrating.collectAsState()

    LightTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightTokens.colors.background)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            if (cameraGranted) {
                MeterBody(
                    settings = settings,
                    meterState = meterState,
                    held = held,
                    calibrating = calibrating,
                    onState = viewModel::onMeterState,
                    onIso = viewModel::setIso,
                    onAperture = viewModel::setAperture,
                    onShutter = viewModel::setShutter,
                    onCalibration = viewModel::setCalibration,
                    onTogglePriority = viewModel::togglePriority,
                    onToggleHold = viewModel::toggleHold,
                    onToggleLens = viewModel::toggleLens,
                    onToggleCalibrating = viewModel::toggleCalibrating,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LightText(
                        text = "The meter needs the camera.",
                        variant = LightVariant.Copy,
                        lighten = true,
                        align = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MeterBody(
    settings: MeterSettings,
    meterState: MeterState,
    held: Boolean,
    calibrating: Boolean,
    onState: (MeterState) -> Unit,
    onIso: (Int) -> Unit,
    onAperture: (Int) -> Unit,
    onShutter: (Int) -> Unit,
    onCalibration: (Int) -> Unit,
    onTogglePriority: () -> Unit,
    onToggleHold: () -> Unit,
    onToggleLens: () -> Unit,
    onToggleCalibrating: () -> Unit,
) {
    CameraPreview(
        lens = settings.lens,
        locked = held,
        onState = onState,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    )

    Spacer(modifier = Modifier.height(8.dp))

    Readout(
        meterState = meterState,
        settings = settings,
        held = held,
        modifier = Modifier.weight(1f),
    )

    if (calibrating) {
        WheelPicker(
            title = "CALIBRATION",
            items = CalibrationScale,
            selectedIndex = CalibrationScale.indexOf(settings.calibrationThirds)
                .coerceAtLeast(0),
            onSelected = { onCalibration(CalibrationScale[it]) },
            label = ::formatCalibration,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WheelPicker(
                title = "ISO",
                items = IsoScale,
                selectedIndex = settings.isoIndex,
                onSelected = onIso,
                label = { it.toString() },
                modifier = Modifier.weight(1f),
            )
            // Only the locked setting gets a wheel; the other one is the answer,
            // so putting it on a wheel would invite you to argue with the meter.
            when (settings.priority) {
                Priority.Aperture -> WheelPicker(
                    title = "APERTURE",
                    items = ApertureScale,
                    selectedIndex = settings.apertureIndex,
                    onSelected = onAperture,
                    label = ::formatAperture,
                    modifier = Modifier.weight(1f),
                )

                Priority.Shutter -> WheelPicker(
                    title = "SHUTTER",
                    items = ShutterScale,
                    selectedIndex = settings.shutterIndex,
                    onSelected = onShutter,
                    label = { it.label },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Control(
            text = if (settings.priority == Priority.Aperture) "A" else "S",
            onClick = onTogglePriority,
        )
        Control(text = if (held) "HELD" else "HOLD", onClick = onToggleHold)
        Control(
            text = if (settings.lens == Lens.Back) "REAR" else "FRONT",
            onClick = onToggleLens,
        )
        Control(text = if (calibrating) "DONE" else "CAL", onClick = onToggleCalibrating)
    }
}

@Composable
private fun Control(text: String, onClick: () -> Unit) {
    LightText(
        text = text,
        variant = LightVariant.Button,
        modifier = Modifier
            .lightClickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
    )
}

/**
 * The answer, and the working behind it. The marked setting is what you dial in;
 * the line beneath says how far off the mark the light actually falls, because a
 * meter that hides a two-thirds-stop rounding is lying by omission.
 */
@Composable
private fun Readout(
    meterState: MeterState,
    settings: MeterSettings,
    held: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (meterState) {
            is MeterState.Warming -> {
                LightText(text = "--", variant = LightVariant.Readout)
                LightText(text = "metering", variant = LightVariant.Detail, lighten = true)
            }

            is MeterState.Failed -> {
                LightText(
                    text = meterState.message,
                    variant = LightVariant.Copy,
                    align = TextAlign.Center,
                    lighten = true,
                )
            }

            is MeterState.Reading -> {
                val result = meter(
                    ev100 = meterState.ev100,
                    filmIso = settings.iso,
                    locked = settings.locked,
                    calibrationEv = settings.calibrationEv,
                )
                LightText(text = answerLine(result), variant = LightVariant.Readout)
                LightText(
                    text = detailLine(result),
                    variant = LightVariant.Detail,
                    lighten = true,
                    align = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LightText(
                    text = "EV " + oneDecimal(result.ev100) +
                        "   ISO " + settings.iso +
                        "   " + lockedLabel(settings) +
                        if (held) "   HELD" else "",
                    variant = LightVariant.Micro,
                    lighten = true,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

/** What you set, echoed back so the answer is never read out of context. */
private fun lockedLabel(settings: MeterSettings): String = when (settings.priority) {
    Priority.Aperture -> formatAperture(settings.aperture)
    Priority.Shutter -> settings.shutter.label
}

/** A speed if you locked the aperture, an f-stop if you locked the speed. */
private fun answerLine(result: MeterResult): String = when (result.fit) {
    DialFit.TooDark -> "LOW"
    DialFit.TooBright -> "HIGH"
    DialFit.InRange -> when (val answer = result.solution) {
        is Solution.Shutter -> answer.snapped.mark.label
        is Solution.Aperture -> formatAperture(answer.snapped.mark)
    }
}

private fun detailLine(result: MeterResult): String {
    val outOfRange = when (result.solution) {
        is Solution.Shutter -> when (result.fit) {
            DialFit.TooDark -> "past 30s - open up or use faster film"
            DialFit.TooBright -> "past 1/8000 - stop down or use slower film"
            DialFit.InRange -> null
        }

        is Solution.Aperture -> when (result.fit) {
            DialFit.TooDark -> "wider than f/1 - slow the shutter or use faster film"
            DialFit.TooBright -> "past f/45 - shorten the shutter or use slower film"
            DialFit.InRange -> null
        }
    }
    if (outOfRange != null) return outOfRange
    if (result.isClean) return "on the mark"

    val exact = when (val answer = result.solution) {
        is Solution.Shutter -> formatSeconds(answer.exactSeconds)
        is Solution.Aperture -> formatAperture(round(answer.exactValue * 10) / 10)
    }
    val direction = if (result.stopsUnder > 0) "under" else "over"
    return exact + " exact   " + oneDecimal(abs(result.stopsUnder)) + " stop " + direction
}

private fun oneDecimal(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return rounded.toString()
}
