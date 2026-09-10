package com.outofthewhale.lightmeter

import kotlin.math.abs
import kotlin.math.log2

/**
 * The marked scales of a film camera. These are *nominal* values - the numbers
 * actually engraved on a lens barrel and a shutter dial - not their exact
 * geometric equivalents. f/1.2 is really f/1.189 and a "1/60" shutter really
 * fires for 1/64s, but nobody sets 1/64 on a Nikon FM. Metering against the
 * nominal number is what every handheld meter does and it keeps the readout
 * honest: what the tool prints is what you dial in. The worst-case error from
 * this is about a tenth of a stop, well inside film's latitude.
 *
 * Each scale comes in three increments because cameras do. A body with
 * full-stop detents cannot be set to f/7.1, and a meter that offers it is
 * inviting you to dial in something your camera has no way to accept.
 */
enum class Step(val label: String) {
    Full("FULL"),
    Half("HALF"),
    Third("THIRD"),
}

/** A marked shutter speed, carrying its own label so the printing stays exact. */
data class ShutterSpeed(val seconds: Double, val label: String)

private fun frac(denominator: Int) = ShutterSpeed(1.0 / denominator, "1/$denominator")

private fun secs(seconds: Double, label: String) = ShutterSpeed(seconds, label)

// ---------------------------------------------------------------- film speed

/** Film speeds, ISO 5800 third-stop series. Slow end covers Adox CMS 20 and friends. */
private val IsoThirds: List<Int> = listOf(
    12, 16, 20, 25, 32, 40, 50, 64, 80,
    100, 125, 160, 200, 250, 320, 400, 500, 640, 800,
    1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400,
)

/** Every third entry of the third-stop series, so the two agree exactly. */
private val IsoFulls: List<Int> = IsoThirds.filterIndexed { index, _ -> index % 3 == 0 }

/** Half stops are their own printed series - 140, 280, 560 rather than 125, 250, 500. */
private val IsoHalves: List<Int> = listOf(
    12, 17, 25, 35, 50, 70,
    100, 140, 200, 280, 400, 560,
    800, 1100, 1600, 2200, 3200, 4500, 6400,
)

fun isoScale(step: Step): List<Int> = when (step) {
    Step.Full -> IsoFulls
    Step.Half -> IsoHalves
    Step.Third -> IsoThirds
}

// ------------------------------------------------------------------ aperture

/** f-numbers, third-stop series. Includes the marked oddballs (1.8, 3.5, 4.5). */
private val ApertureThirds: List<Double> = listOf(
    1.0, 1.1, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5,
    4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0, 9.0, 10.0, 11.0, 13.0, 14.0,
    16.0, 18.0, 20.0, 22.0, 25.0, 29.0, 32.0, 36.0, 40.0, 45.0,
)

/** Every third entry, which lands exactly on the engraved full stops. */
private val ApertureFulls: List<Double> =
    ApertureThirds.filterIndexed { index, _ -> index % 3 == 0 }

private val ApertureHalves: List<Double> = listOf(
    1.0, 1.2, 1.4, 1.7, 2.0, 2.4, 2.8, 3.3, 4.0, 4.8, 5.6, 6.7,
    8.0, 9.5, 11.0, 13.0, 16.0, 19.0, 22.0, 27.0, 32.0, 38.0, 45.0,
)

fun apertureScale(step: Step): List<Double> = when (step) {
    Step.Full -> ApertureFulls
    Step.Half -> ApertureHalves
    Step.Third -> ApertureThirds
}

// ------------------------------------------------------------------- shutter

/** Full-stop shutter dial, 1/8000 to 30s - the span of a film body. */
private val ShutterFulls: List<ShutterSpeed> = listOf(
    frac(8000), frac(4000), frac(2000), frac(1000), frac(500), frac(250),
    frac(125), frac(60), frac(30), frac(15), frac(8), frac(4), frac(2),
    secs(1.0, "1s"), secs(2.0, "2s"), secs(4.0, "4s"), secs(8.0, "8s"),
    secs(15.0, "15s"), secs(30.0, "30s"),
)

private val ShutterHalves: List<ShutterSpeed> = listOf(
    frac(8000), frac(6000), frac(4000), frac(3000), frac(2000), frac(1500),
    frac(1000), frac(750), frac(500), frac(350), frac(250), frac(180),
    frac(125), frac(90), frac(60), frac(45), frac(30), frac(20),
    frac(15), frac(10), frac(8), frac(6), frac(4), frac(3), frac(2),
    secs(0.7, "0.7s"), secs(1.0, "1s"), secs(1.5, "1.5s"), secs(2.0, "2s"),
    secs(3.0, "3s"), secs(4.0, "4s"), secs(6.0, "6s"), secs(8.0, "8s"),
    secs(12.0, "12s"), secs(15.0, "15s"), secs(20.0, "20s"), secs(30.0, "30s"),
)

private val ShutterThirds: List<ShutterSpeed> = listOf(
    frac(8000), frac(6400), frac(5000), frac(4000), frac(3200), frac(2500),
    frac(2000), frac(1600), frac(1250), frac(1000), frac(800), frac(640),
    frac(500), frac(400), frac(320), frac(250), frac(200), frac(160),
    frac(125), frac(100), frac(80), frac(60), frac(50), frac(40),
    frac(30), frac(25), frac(20), frac(15), frac(13), frac(10),
    frac(8), frac(6), frac(5), frac(4), frac(3),
    secs(0.4, "0.4s"), frac(2), secs(0.6, "0.6s"), secs(0.8, "0.8s"),
    secs(1.0, "1s"), secs(1.3, "1.3s"), secs(1.6, "1.6s"), secs(2.0, "2s"),
    secs(2.5, "2.5s"), secs(3.2, "3.2s"), secs(4.0, "4s"), secs(5.0, "5s"),
    secs(6.0, "6s"), secs(8.0, "8s"), secs(10.0, "10s"), secs(13.0, "13s"),
    secs(15.0, "15s"), secs(20.0, "20s"), secs(25.0, "25s"), secs(30.0, "30s"),
)

fun shutterScale(step: Step): List<ShutterSpeed> = when (step) {
    Step.Full -> ShutterFulls
    Step.Half -> ShutterHalves
    Step.Third -> ShutterThirds
}

// -------------------------------------------------------------------- nearest

/**
 * Nearest entry measured in stops, not in arithmetic distance. Switching a wheel
 * from thirds to full stops has to land somewhere, and halfway between f/8 and
 * f/11 is f/9.4 by ratio - not f/9.5 by subtraction.
 */
fun nearestIso(scale: List<Int>, value: Int): Int =
    scale.minBy { abs(log2(it.toDouble() / value)) }

fun nearestAperture(scale: List<Double>, value: Double): Double =
    scale.minBy { abs(log2(it / value)) }

fun nearestShutter(scale: List<ShutterSpeed>, seconds: Double): ShutterSpeed =
    scale.minBy { abs(log2(it.seconds / seconds)) }

// ------------------------------------------------------------------ printing

/** Render an f-number the way a lens barrel does: f/8, not f/8.0. */
fun formatAperture(value: Double): String {
    val whole = value == value.toInt().toDouble()
    return if (whole) "f/${value.toInt()}" else "f/$value"
}

/**
 * Render an arbitrary duration in shutter-dial language. Used for the exact,
 * unrounded recommendation sitting under the snapped one.
 */
fun formatSeconds(seconds: Double): String = when {
    seconds <= 0.0 || !seconds.isFinite() -> "--"
    seconds >= 60.0 -> "${(seconds / 60).roundToPlaces(1)}min"
    seconds >= 1.0 -> "${seconds.roundToPlaces(1)}s"
    else -> "1/${(1.0 / seconds).roundToSignificantMarked()}"
}

private fun Double.roundToPlaces(places: Int): String {
    val factor = generateSequence(1.0) { it * 10 }.elementAt(places)
    val rounded = kotlin.math.round(this * factor) / factor
    return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
}

/**
 * Reciprocals round to whole numbers - "1/91", not "1/90.7". Rounding any
 * coarser than this would corrupt the engraved speeds themselves: to two
 * significant figures 125 becomes 120, which is not a number on any dial.
 */
private fun Double.roundToSignificantMarked(): Int = when {
    this >= 1000 -> (kotlin.math.round(this / 10) * 10).toInt()
    else -> kotlin.math.round(this).toInt()
}
