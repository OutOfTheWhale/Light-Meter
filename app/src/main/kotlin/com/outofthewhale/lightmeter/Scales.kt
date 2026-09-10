package com.outofthewhale.lightmeter

/**
 * The marked scales of a film camera. These are *nominal* values - the numbers
 * actually engraved on a lens barrel and a shutter dial - not their exact
 * geometric equivalents. f/1.2 is really f/1.189 and a "1/60" shutter really
 * fires for 1/64s, but nobody sets 1/64 on a Nikon FM. Metering against the
 * nominal number is what every handheld meter does and it keeps the readout
 * honest: what the tool prints is what you dial in. The worst-case error from
 * this is about a tenth of a stop, well inside film's latitude.
 */

/** Film speeds, ISO 5800 third-stop series. Slow end covers Adox CMS 20 and friends. */
val IsoScale: List<Int> = listOf(
    12, 16, 20, 25, 32, 40, 50, 64, 80,
    100, 125, 160, 200, 250, 320, 400, 500, 640, 800,
    1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400,
)

/** Index of ISO 400 - a reasonable place to park the wheel on first launch. */
val DefaultIsoIndex: Int = IsoScale.indexOf(400)

/** f-numbers, third-stop series. Includes the marked oddballs (1.8, 3.5, 4.5). */
val ApertureScale: List<Double> = listOf(
    1.0, 1.1, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5,
    4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0, 9.0, 10.0, 11.0, 13.0, 14.0,
    16.0, 18.0, 20.0, 22.0, 25.0, 29.0, 32.0, 36.0, 40.0, 45.0,
)

/** Index of f/8 - the middle of most lenses and a sane default. */
val DefaultApertureIndex: Int = ApertureScale.indexOf(8.0)

/**
 * A marked shutter speed. [seconds] is the reciprocal of the marked number for
 * fractions, so the arithmetic matches the label rather than the true timing.
 */
data class ShutterSpeed(val seconds: Double, val label: String)

/** Full-stop shutter dial, 1/8000 to 30s, the usable span of a film body plus bulb range. */
val ShutterScale: List<ShutterSpeed> = listOf(
    ShutterSpeed(1.0 / 8000, "1/8000"),
    ShutterSpeed(1.0 / 4000, "1/4000"),
    ShutterSpeed(1.0 / 2000, "1/2000"),
    ShutterSpeed(1.0 / 1000, "1/1000"),
    ShutterSpeed(1.0 / 500, "1/500"),
    ShutterSpeed(1.0 / 250, "1/250"),
    ShutterSpeed(1.0 / 125, "1/125"),
    ShutterSpeed(1.0 / 60, "1/60"),
    ShutterSpeed(1.0 / 30, "1/30"),
    ShutterSpeed(1.0 / 15, "1/15"),
    ShutterSpeed(1.0 / 8, "1/8"),
    ShutterSpeed(1.0 / 4, "1/4"),
    ShutterSpeed(1.0 / 2, "1/2"),
    ShutterSpeed(1.0, "1s"),
    ShutterSpeed(2.0, "2s"),
    ShutterSpeed(4.0, "4s"),
    ShutterSpeed(8.0, "8s"),
    ShutterSpeed(15.0, "15s"),
    ShutterSpeed(30.0, "30s"),
)

/** Index of 1/125 - a hand-holdable default for the shutter wheel. */
val DefaultShutterIndex: Int = ShutterScale.indexOfFirst { it.label == "1/125" }

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
