package com.outofthewhale.lightmeter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/**
 * Where the wheels were left. A meter you pick up should still be set for the
 * roll that is already in the camera.
 *
 * Calibration is held in thirds of a stop because that is the resolution of the
 * wheel that sets it; storing the double would invite drift on every round trip.
 */
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

data class MeterSettings(
    val isoIndex: Int = DefaultIsoIndex,
    val apertureIndex: Int = DefaultApertureIndex,
    val shutterIndex: Int = DefaultShutterIndex,
    val priority: Priority = Priority.Aperture,
    val calibrationThirds: Int = 0,
    val lens: Lens = Lens.Back,
) {
    val iso: Int get() = IsoScale[isoIndex.coerceIn(IsoScale.indices)]
    val aperture: Double get() = ApertureScale[apertureIndex.coerceIn(ApertureScale.indices)]
    val shutter: ShutterSpeed get() = ShutterScale[shutterIndex.coerceIn(ShutterScale.indices)]
    val calibrationEv: Double get() = calibrationThirds / 3.0

    val locked: Locked
        get() = when (priority) {
            Priority.Aperture -> Locked.Aperture(aperture)
            Priority.Shutter -> Locked.Shutter(shutter)
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

    private val isoKey = intPreferencesKey("isoIndex")
    private val apertureKey = intPreferencesKey("apertureIndex")
    private val shutterKey = intPreferencesKey("shutterIndex")
    private val priorityKey = stringPreferencesKey("priority")
    private val calibrationKey = intPreferencesKey("calibrationThirds")
    private val lensKey = stringPreferencesKey("lens")

    val settings: Flow<MeterSettings> = dataStore.data.map { preferences ->
        MeterSettings(
            isoIndex = preferences[isoKey] ?: DefaultIsoIndex,
            apertureIndex = preferences[apertureKey] ?: DefaultApertureIndex,
            shutterIndex = preferences[shutterKey] ?: DefaultShutterIndex,
            priority = if (preferences[priorityKey] == Priority.Shutter.name) {
                Priority.Shutter
            } else {
                Priority.Aperture
            },
            calibrationThirds = preferences[calibrationKey] ?: 0,
            lens = if (preferences[lensKey] == Lens.Front.name) Lens.Front else Lens.Back,
        )
    }

    suspend fun save(settings: MeterSettings) {
        dataStore.edit { preferences ->
            preferences[isoKey] = settings.isoIndex
            preferences[apertureKey] = settings.apertureIndex
            preferences[shutterKey] = settings.shutterIndex
            preferences[priorityKey] = settings.priority.name
            preferences[calibrationKey] = settings.calibrationThirds
            preferences[lensKey] = settings.lens.name
        }
    }
}
