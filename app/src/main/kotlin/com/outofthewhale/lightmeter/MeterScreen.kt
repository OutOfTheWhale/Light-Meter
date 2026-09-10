package com.outofthewhale.lightmeter

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun MeterScreen(viewModel: MeterViewModel, cameraGranted: Boolean) {
    val settings by viewModel.settings.collectAsState()
    val meterState by viewModel.meterState.collectAsState()
    val held by viewModel.held.collectAsState()
    val showingSettings by viewModel.showingSettings.collectAsState()
    val loaded by viewModel.loaded.collectAsState()

    LightTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightTokens.colors.background)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            if (!cameraGranted) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LightText(
                        text = "The meter needs the camera.",
                        variant = LightVariant.Copy,
                        lighten = true,
                        align = TextAlign.Center,
                    )
                }
                return@Column
            }

            // Metering runs whether or not the settings sheet is over it, so the
            // reading is already settled when you come back out.
            CameraMetering(
                lens = settings.lens,
                locked = held,
                onState = viewModel::onMeterState,
            )

            if (!loaded) return@Column

            if (showingSettings) {
                SettingsBody(
                    settings = settings,
                    onCalibration = viewModel::setCalibration,
                    onClose = viewModel::closeSettings,
                )
            } else {
                MeterBody(
                    settings = settings,
                    meterState = meterState,
                    held = held,
                    onIso = viewModel::setIso,
                    onAperture = viewModel::setAperture,
                    onShutter = viewModel::setShutter,
                    onTogglePriority = viewModel::togglePriority,
                    onToggleHold = viewModel::toggleHold,
                    onToggleLens = viewModel::toggleLens,
                    onOpenSettings = viewModel::openSettings,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.MeterBody(
    settings: MeterSettings,
    meterState: MeterState,
    held: Boolean,
    onIso: (Int) -> Unit,
    onAperture: (Int) -> Unit,
    onShutter: (Int) -> Unit,
    onTogglePriority: () -> Unit,
    onToggleHold: () -> Unit,
    onToggleLens: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "Settings",
            colorFilter = ColorFilter.tint(LightTokens.colors.contentSecondary),
            modifier = Modifier
                .size(26.dp)
                .lightClickable { onOpenSettings() },
        )
    }

    Readout(
        meterState = meterState,
        settings = settings,
        modifier = Modifier.weight(1f),
    )

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
        // Only the locked setting gets a wheel; the other one is the answer, so
        // putting it on a wheel would invite you to argue with the meter.
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

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Control(
            text = if (settings.priority == Priority.Aperture) "LOCK A" else "LOCK S",
            onClick = onTogglePriority,
        )
        Control(text = if (held) "HELD" else "HOLD", onClick = onToggleHold)
        Control(
            text = if (settings.lens == Lens.Back) "REAR" else "FRONT",
            onClick = onToggleLens,
        )
    }
}

@Composable
private fun ColumnScope.SettingsBody(
    settings: MeterSettings,
    onCalibration: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Control(text = "DONE", onClick = onClose)
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LightText(text = "CALIBRATION", variant = LightVariant.Micro, lighten = true)
        Spacer(modifier = Modifier.height(10.dp))
        LightText(
            text = formatCalibration(settings.calibrationThirds) + " stops",
            variant = LightVariant.Heading,
        )
        Spacer(modifier = Modifier.height(20.dp))
        LightText(
            text = "Meter something against a meter you trust, then dial until they agree.",
            variant = LightVariant.Detail,
            lighten = true,
            align = TextAlign.Center,
        )
    }

    WheelPicker(
        title = "STOPS",
        items = CalibrationScale,
        selectedIndex = CalibrationScale.indexOf(settings.calibrationThirds).coerceAtLeast(0),
        onSelected = { onCalibration(CalibrationScale[it]) },
        label = ::formatCalibration,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(12.dp))
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
 * The answer, alone. Everything needed to interpret it - which film speed, which
 * setting is locked - is already on the wheels a thumb's width below, so
 * repeating it here only crowds the one number you came to read.
 */
@Composable
private fun Readout(
    meterState: MeterState,
    settings: MeterSettings,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when (meterState) {
            is MeterState.Warming -> LightText(text = "--", variant = LightVariant.Readout)

            is MeterState.Failed -> LightText(
                text = meterState.message,
                variant = LightVariant.Copy,
                align = TextAlign.Center,
                lighten = true,
            )

            is MeterState.Reading -> {
                val result = meter(
                    ev100 = meterState.ev100,
                    filmIso = settings.iso,
                    locked = settings.locked,
                    calibrationEv = settings.calibrationEv,
                )
                LightText(
                    text = answerLine(result),
                    variant = LightVariant.Readout,
                    align = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
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
