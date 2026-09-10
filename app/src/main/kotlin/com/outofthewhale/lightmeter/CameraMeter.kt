package com.outofthewhale.lightmeter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/** Which lens is doing the metering. */
enum class Lens { Back, Front }

/** What the phone's auto-exposure settled on, before any conversion. */
data class CameraExposure(
    val exposureSeconds: Double,
    val iso: Int,
    val aperture: Double,
)

sealed interface MeterState {
    /** Camera opening, or auto-exposure still hunting. */
    data object Warming : MeterState

    data class Reading(
        val ev100: Double,
        val exposure: CameraExposure,
        val locked: Boolean,
    ) : MeterState

    data class Failed(val message: String) : MeterState
}

/**
 * A reflected-light meter built on the phone's own auto-exposure.
 *
 * The trick is not to fight the camera. Left in auto, the AE loop solves for a
 * correctly exposed frame and publishes its answer in every CaptureResult; that
 * answer is a measurement of the scene. So this opens the camera, lets AE
 * converge, and reads the triplet back out.
 *
 * There is no preview. A capture session still needs somewhere to put its
 * frames, so it gets a small ImageReader whose images are closed the moment they
 * arrive - the pixels are never looked at and never leave the buffer. Metering
 * needs the metadata, not the picture, and a meter you have to watch on screen
 * is a worse meter than one you can point without looking.
 */
class CameraMeter(context: Context) {

    private val _state = MutableStateFlow<MeterState>(MeterState.Warming)
    val state: StateFlow<MeterState> = _state

    private val manager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var surface: Surface? = null
    private var characteristics: CameraCharacteristics? = null

    /** Smoothed scene value, so the readout does not shiver on AE's small corrections. */
    private var smoothedEv: Double? = null
    private var aeLocked = false

    fun start(lens: Lens) {
        stop()
        val cameraManager = manager ?: run {
            _state.value = MeterState.Failed("No camera on this device.")
            return
        }
        val id = cameraIdFor(cameraManager, lens) ?: run {
            _state.value = MeterState.Failed(
                if (lens == Lens.Front) "No front camera." else "No rear camera.",
            )
            return
        }

        _state.value = MeterState.Warming
        smoothedEv = null
        characteristics = cameraManager.getCameraCharacteristics(id)

        val started = HandlerThread("light-meter-camera").also { it.start() }
        thread = started
        handler = Handler(started.looper)

        val size = meteringSize()
        val imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
        // Frames must be drained or the pipeline stalls and AE stops updating.
        // Closing them immediately is the whole of our interest in the pixels.
        imageReader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close() }, handler)
        reader = imageReader
        surface = imageReader.surface

        openCamera(cameraManager, id)
    }

    // Permission is checked by the screen before metering ever starts; lint
    // cannot see across that boundary.
    @SuppressLint("MissingPermission")
    private fun openCamera(cameraManager: CameraManager, id: String) {
        try {
            cameraManager.openCamera(id, deviceCallback, handler)
        } catch (e: SecurityException) {
            _state.value = MeterState.Failed("Camera permission was refused.")
        } catch (e: Exception) {
            _state.value = MeterState.Failed("Could not open the camera: " + e.message)
        }
    }

    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            device = camera
            configureSession(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            device = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            device = null
            _state.value = MeterState.Failed("Camera error " + error + ".")
        }
    }

    @Suppress("DEPRECATION")
    private fun configureSession(camera: CameraDevice) {
        val target = surface ?: return
        try {
            camera.createCaptureSession(
                listOf(target),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        session = configured
                        startMetering()
                    }

                    override fun onConfigureFailed(configured: CameraCaptureSession) {
                        _state.value = MeterState.Failed("Could not start metering.")
                    }
                },
                handler,
            )
        } catch (e: Exception) {
            _state.value = MeterState.Failed("Could not start metering: " + e.message)
        }
    }

    private fun startMetering() {
        val camera = device ?: return
        val captureSession = session ?: return
        val target = surface ?: return
        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(target)
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            // A meter that fires a flash is measuring its own flash.
            set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
            set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
            set(CaptureRequest.CONTROL_AE_LOCK, aeLocked)
        }
        try {
            captureSession.setRepeatingRequest(request.build(), captureCallback, handler)
        } catch (e: Exception) {
            _state.value = MeterState.Failed("Could not meter: " + e.message)
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            consume(result)
        }
    }

    private fun consume(result: CaptureResult) {
        val exposureNanos = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val sensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY)
        if (exposureNanos == null || sensitivity == null) {
            // Without these two the camera is not telling us what it did, and
            // there is no honest reading to be had.
            _state.value = MeterState.Failed("This camera does not report its exposure.")
            return
        }

        val aperture = result.get(CaptureResult.LENS_APERTURE)?.toDouble() ?: fixedAperture()
        if (aperture == null) {
            _state.value = MeterState.Failed("This camera does not report its aperture.")
            return
        }

        // Some sensors apply extra gain after the raw read; AE counts it as part
        // of the exposure, so the reading must too.
        val boost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST) ?: 100
        val effectiveIso = (sensitivity * boost / 100).coerceAtLeast(1)

        val seconds = exposureNanos / 1_000_000_000.0
        if (seconds <= 0.0) return

        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val settled = aeState == null ||
            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
            aeState == CaptureResult.CONTROL_AE_STATE_LOCKED ||
            aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
        if (!settled) {
            if (_state.value !is MeterState.Reading) _state.value = MeterState.Warming
            return
        }

        val measured = ev100(seconds, effectiveIso, aperture)
        // Track small AE corrections gently, but follow a real scene change at once.
        val previous = smoothedEv
        val next = if (previous == null || abs(measured - previous) > 1.5) {
            measured
        } else {
            previous + (measured - previous) * 0.25
        }
        smoothedEv = next

        _state.value = MeterState.Reading(
            ev100 = next,
            exposure = CameraExposure(seconds, effectiveIso, aperture),
            locked = aeLocked,
        )
    }

    /** Hold the reading so it can be carried away from the scene to the camera. */
    fun setLocked(locked: Boolean) {
        if (aeLocked == locked) return
        aeLocked = locked
        val current = _state.value
        if (current is MeterState.Reading) _state.value = current.copy(locked = locked)
        startMetering()
    }

    fun stop() {
        try {
            session?.stopRepeating()
        } catch (e: Exception) {
            // The session may already be gone; nothing to unwind.
        }
        session?.close()
        session = null
        device?.close()
        device = null
        surface = null
        reader?.close()
        reader = null
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun fixedAperture(): Double? =
        characteristics
            ?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.firstOrNull()
            ?.toDouble()

    private fun cameraIdFor(cameraManager: CameraManager, lens: Lens): String? {
        val wanted = when (lens) {
            Lens.Back -> CameraCharacteristics.LENS_FACING_BACK
            Lens.Front -> CameraCharacteristics.LENS_FACING_FRONT
        }
        return try {
            cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == wanted
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Nothing reads these frames, so the smallest usable buffer is the right
     * one: it keeps the pipeline cheap and the AE loop quick.
     */
    private fun meteringSize(): Size {
        val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return Size(640, 480)
        val sizes = map.getOutputSizes(ImageFormat.YUV_420_888) ?: return Size(640, 480)
        return sizes
            .filter { it.width >= 320 && it.height >= 240 }
            .minByOrNull { it.width.toLong() * it.height }
            ?: sizes.minByOrNull { it.width.toLong() * it.height }
            ?: Size(640, 480)
    }
}
