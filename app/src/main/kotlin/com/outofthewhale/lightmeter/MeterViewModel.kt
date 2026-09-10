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

    /** Calibration lives behind the settings icon rather than on the meter face. */
    private val _showingSettings = MutableStateFlow(false)
    val showingSettings: StateFlow<Boolean> = _showingSettings

    /**
     * The wheels capture their scroll position the first time they compose, so
     * they must not compose until the stored settings have arrived - otherwise a
     * wheel sits on the default value while the model holds the saved one, and
     * the highlight lands on neither.
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        viewModelScope.launch {
            _settings.value = store.settings.first()
            _loaded.value = true
        }
    }

    fun onMeterState(state: MeterState) {
        _meterState.value = state
    }

    private fun update(transform: (MeterSettings) -> MeterSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        viewModelScope.launch { store.save(next) }
    }

    fun setIso(index: Int) = update { settings ->
        settings.copy(iso = settings.isoOptions.getOrElse(index) { settings.iso })
    }

    fun setAperture(index: Int) = update { settings ->
        settings.copy(aperture = settings.apertureOptions.getOrElse(index) { settings.aperture })
    }

    fun setShutter(index: Int) = update { settings ->
        val marked = settings.shutterOptions.getOrNull(index)
        settings.copy(shutterSeconds = marked?.seconds ?: settings.shutterSeconds)
    }

    /**
     * Changing an increment keeps the value and moves it to the nearest mark the
     * new increment offers, rather than keeping the position and silently
     * changing the value underneath it.
     */
    fun setIsoStep(step: Step) = update { settings ->
        settings.copy(isoStep = step, iso = nearestIso(isoScale(step), settings.iso))
    }

    fun setApertureStep(step: Step) = update { settings ->
        settings.copy(
            apertureStep = step,
            aperture = nearestAperture(apertureScale(step), settings.aperture),
        )
    }

    fun setShutterStep(step: Step) = update { settings ->
        settings.copy(
            shutterStep = step,
            shutterSeconds = nearestShutter(
                shutterScale(step),
                settings.shutterSeconds,
            ).seconds,
        )
    }

    fun setCalibration(thirds: Int) = update { it.copy(calibrationThirds = thirds) }

    /**
     * Swapping which setting is held still carries the current answer across, so
     * the exposure you were looking at does not jump when you change your mind
     * about which dial you are willing to move.
     */
    fun togglePriority() = update { settings ->
        val reading = _meterState.value as? MeterState.Reading
        val solved = reading?.let {
            meter(
                ev100 = it.ev100,
                filmIso = settings.markedIso,
                locked = settings.locked,
                dials = settings.dials,
                calibrationEv = settings.calibrationEv,
            )
        }?.solution
        when (settings.priority) {
            Priority.Aperture -> settings.copy(
                priority = Priority.Shutter,
                shutterSeconds = (solved as? Solution.Shutter)
                    ?.snapped?.mark?.seconds
                    ?: settings.shutterSeconds,
            )

            Priority.Shutter -> settings.copy(
                priority = Priority.Aperture,
                aperture = (solved as? Solution.Aperture)
                    ?.snapped?.mark
                    ?: settings.aperture,
            )
        }
    }

    fun toggleLens() = update {
        it.copy(lens = if (it.lens == Lens.Back) Lens.Front else Lens.Back)
    }

    fun toggleHold() {
        _held.value = !_held.value
    }

    fun openSettings() {
        _showingSettings.value = true
    }

    fun closeSettings() {
        _showingSettings.value = false
    }

    /** Coming back to the app should meter the room you are in now. */
    fun onResumed() {
        _held.value = false
    }
}
