package com.outofthewhale.lightmeter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MeterViewModel(private val store: SettingsStore) : ViewModel() {

    private val _settings = MutableStateFlow(MeterSettings())
    val settings: StateFlow<MeterSettings> = _settings

    private val _meterState = MutableStateFlow<MeterState>(MeterState.Warming)
    val meterState: StateFlow<MeterState> = _meterState

    /** Whether the reading is being held so it can be carried to the camera. */
    private val _held = MutableStateFlow(false)
    val held: StateFlow<Boolean> = _held

    /** The wheels swap to calibration rather than competing for the same rows. */
    private val _calibrating = MutableStateFlow(false)
    val calibrating: StateFlow<Boolean> = _calibrating

    init {
        viewModelScope.launch { _settings.value = store.settings.first() }
    }

    fun onMeterState(state: MeterState) {
        _meterState.value = state
    }

    private fun update(transform: (MeterSettings) -> MeterSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        viewModelScope.launch { store.save(next) }
    }

    fun setIso(index: Int) = update { it.copy(isoIndex = index) }

    fun setAperture(index: Int) = update { it.copy(apertureIndex = index) }

    fun setShutter(index: Int) = update { it.copy(shutterIndex = index) }

    fun setCalibration(thirds: Int) = update { it.copy(calibrationThirds = thirds) }

    /**
     * Swapping which setting is held still carries the current answer across, so
     * the exposure you were looking at does not jump when you change your mind
     * about which dial you are willing to move.
     */
    fun togglePriority() = update { settings ->
        val reading = _meterState.value as? MeterState.Reading
        val solved = reading?.let {
            meter(it.ev100, settings.iso, settings.locked, settings.calibrationEv)
        }?.solution
        when (settings.priority) {
            Priority.Aperture -> settings.copy(
                priority = Priority.Shutter,
                shutterIndex = (solved as? Solution.Shutter)
                    ?.let { ShutterScale.indexOf(it.snapped.mark) }
                    ?.takeIf { it >= 0 }
                    ?: settings.shutterIndex,
            )

            Priority.Shutter -> settings.copy(
                priority = Priority.Aperture,
                apertureIndex = (solved as? Solution.Aperture)
                    ?.let { ApertureScale.indexOf(it.snapped.mark) }
                    ?.takeIf { it >= 0 }
                    ?: settings.apertureIndex,
            )
        }
    }

    fun toggleLens() = update {
        it.copy(lens = if (it.lens == Lens.Back) Lens.Front else Lens.Back)
    }

    fun toggleHold() {
        _held.value = !_held.value
    }

    fun toggleCalibrating() {
        _calibrating.value = !_calibrating.value
    }

    /** Coming back to the app should meter the room you are in now. */
    fun onResumed() {
        _held.value = false
    }
}
