package com.outofthewhale.lightmeter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/**
 * Which setting the photographer is holding still. The meter solves for the
 * other one and shows it as the answer.
 */
enum class Priority {
    /** You chose the f-stop; the meter gives you a shutter speed. */
    Aperture,

    /** You chose the shutter speed; the meter gives you an f-stop. */
    Shutter,
}

/**
 * Where the wheels were left. A meter you pick up should still be set for the
 * roll that is already in the camera.
 *
 * Settings hold *values*, not positions on a wheel. An index means nothing once
 * the increment changes underneath it - position 15 is ISO 400 in thirds and
 * ISO 6400 in full stops - so the film speed is stored as a film speed and the
 * nearest available mark is found when it is needed.
 *
 * Calibration is held in thirds of a stop because that is the resolution of the
 * wheel that sets it; storing the double would invite drift on every round trip.
 */
data class MeterSettings(
    val iso: Int = 400,
    val aperture: Double = 8.0,
    val shutterSeconds: Double = 1.0 / 125,
    val isoStep: Step = Step.Third,
    val apertureStep: Step = Step.Third,
    val shutterStep: Step = Step.Full,
    val priority: Priority = Priority.Aperture,
    val calibrationThirds: Int = 0,
    val lens: Lens = Lens.Back,
) {
    val isoOptions: List<Int> get() = isoScale(isoStep)
    val apertureOptions: List<Double> get() = apertureScale(apertureStep)
    val shutterOptions: List<ShutterSpeed> get() = shutterScale(shutterStep)

    /** The stored value pinned to the nearest mark the current increment offers. */
    val markedIso: Int get() = nearestIso(isoOptions, iso)
    val markedAperture: Double get() = nearestAperture(apertureOptions, aperture)
    val markedShutter: ShutterSpeed get() = nearestShutter(shutterOptions, shutterSeconds)

    val calibrationEv: Double get() = calibrationThirds / 3.0

    val dials: Dials get() = Dials(aperture = apertureOptions, shutter = shutterOptions)

    val locked: Locked
        get() = when (priority) {
            Priority.Aperture -> Locked.Aperture(markedAperture)
            Priority.Shutter -> Locked.Shutter(markedShutter)
        }
}

/** The calibration wheel: plus or minus three stops, in thirds. */
val CalibrationScale: List<Int> =
    ((-MaxCalibrationEv * 3).roundToInt()..(MaxCalibrationEv * 3).roundToInt()).toList()

fun formatCalibration(thirds: Int): String {
    if (thirds == 0) return "0"
    val sign = if (thirds > 0) "+" else "-"
    val value = kotlin.math.abs(thirds) / 3.0
    val rounded = kotlin.math.round(value * 10) / 10
    val text = if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
    return sign + text
}

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private val isoKey = intPreferencesKey("iso")
    private val apertureKey = doublePreferencesKey("aperture")
    private val shutterKey = doublePreferencesKey("shutterSeconds")
    private val isoStepKey = stringPreferencesKey("isoStep")
    private val apertureStepKey = stringPreferencesKey("apertureStep")
    private val shutterStepKey = stringPreferencesKey("shutterStep")
    private val priorityKey = stringPreferencesKey("priority")
    private val calibrationKey = intPreferencesKey("calibrationThirds")
    private val lensKey = stringPreferencesKey("lens")

    val settings: Flow<MeterSettings> = dataStore.data.map { preferences ->
        val defaults = MeterSettings()
        MeterSettings(
            iso = preferences[isoKey] ?: defaults.iso,
            aperture = preferences[apertureKey] ?: defaults.aperture,
            shutterSeconds = preferences[shutterKey] ?: defaults.shutterSeconds,
            isoStep = preferences[isoStepKey].toStep(defaults.isoStep),
            apertureStep = preferences[apertureStepKey].toStep(defaults.apertureStep),
            shutterStep = preferences[shutterStepKey].toStep(defaults.shutterStep),
            priority = if (preferences[priorityKey] == Priority.Shutter.name) {
                Priority.Shutter
            } else {
                Priority.Aperture
            },
            calibrationThirds = preferences[calibrationKey] ?: defaults.calibrationThirds,
            lens = if (preferences[lensKey] == Lens.Front.name) Lens.Front else Lens.Back,
        )
    }

    suspend fun save(settings: MeterSettings) {
        dataStore.edit { preferences ->
            preferences[isoKey] = settings.iso
            preferences[apertureKey] = settings.aperture
            preferences[shutterKey] = settings.shutterSeconds
            preferences[isoStepKey] = settings.isoStep.name
            preferences[apertureStepKey] = settings.apertureStep.name
            preferences[shutterStepKey] = settings.shutterStep.name
            preferences[priorityKey] = settings.priority.name
            preferences[calibrationKey] = settings.calibrationThirds
            preferences[lensKey] = settings.lens.name
        }
    }

    private fun String?.toStep(fallback: Step): Step =
        Step.entries.firstOrNull { it.name == this } ?: fallback
}
