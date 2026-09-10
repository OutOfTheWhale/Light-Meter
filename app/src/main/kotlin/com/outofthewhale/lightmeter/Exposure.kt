package com.outofthewhale.lightmeter

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The exposure arithmetic, kept free of Android so it can be tested on the JVM.
 *
 * The whole tool rests on one identity: a camera that has metered a scene has
 * already told you how bright that scene is. Once the phone's auto-exposure
 * settles, the triplet it chose - exposure time, sensitivity, f-number - encodes
 * the scene luminance, and normalising that triplet to ISO 100 gives EV100, a
 * number that has nothing to do with the phone any more. From there it is
 * ordinary exposure maths to hand back a shutter speed for a roll of film.
 */

/** How far the meter may be trusted before the reading is worth showing at all. */
const val MaxCalibrationEv: Double = 3.0

/**
 * Scene brightness as EV at ISO 100, derived from what the phone's AE chose.
 *
 * EV at the capturing sensitivity is log2(N^2 / t); subtracting log2(S/100)
 * re-bases it to ISO 100.
 */
fun ev100(
    exposureTimeSeconds: Double,
    sensitivityIso: Int,
    aperture: Double,
): Double {
    require(exposureTimeSeconds > 0) { "exposure time must be positive" }
    require(sensitivityIso > 0) { "sensitivity must be positive" }
    require(aperture > 0) { "aperture must be positive" }
    return log2(aperture * aperture / exposureTimeSeconds) - log2(sensitivityIso / 100.0)
}

/** The same scene expressed at an arbitrary film speed. */
fun evAtFilmSpeed(ev100: Double, filmIso: Int): Double = ev100 + log2(filmIso / 100.0)

/**
 * The shutter time, in seconds, that correctly exposes [filmIso] film at
 * [aperture] under a scene of [ev100]. Unrounded - snapping to the dial is a
 * separate, presentational step.
 */
fun shutterSeconds(ev100: Double, filmIso: Int, aperture: Double): Double =
    aperture * aperture / 2.0.pow(evAtFilmSpeed(ev100, filmIso))

/**
 * The f-number that correctly exposes [filmIso] film at [shutterSeconds] under a
 * scene of [ev100]. The mirror of [shutterSeconds]: exposure goes as N^2/t, so
 * fixing t and solving for N is a square root rather than a division.
 */
fun apertureFor(ev100: Double, filmIso: Int, shutterSeconds: Double): Double =
    sqrt(shutterSeconds * 2.0.pow(evAtFilmSpeed(ev100, filmIso)))

/**
 * A mark on a dial plus the correction it still needs. [stopsUnder] is how many
 * stops of extra light the mark wants to reach the ideal: positive means the
 * mark underexposes, negative means it overexposes. Zero to within a sixth of a
 * stop is, for film, simply correct.
 */
data class Snapped<T>(
    val mark: T,
    val stopsUnder: Double,
) {
    val isClean: Boolean get() = abs(stopsUnder) < 1.0 / 6.0
}

typealias SnappedShutter = Snapped<ShutterSpeed>
typealias SnappedAperture = Snapped<Double>

/** Nearest mark on the shutter dial, measured in stops rather than seconds. */
fun snapShutter(seconds: Double, scale: List<ShutterSpeed> = ShutterScale): SnappedShutter {
    val nearest = scale.minBy { abs(log2(seconds / it.seconds)) }
    return Snapped(nearest, log2(seconds / nearest.seconds))
}

/**
 * Nearest mark on the aperture ring. A wider mark than the ideal lets in more
 * light, so it overexposes - hence the sign flip against the shutter case.
 */
fun snapAperture(value: Double, scale: List<Double> = ApertureScale): SnappedAperture {
    val nearest = scale.minBy { abs(log2(value / it)) }
    return Snapped(nearest, 2 * log2(nearest / value))
}

/** Whether the ideal setting falls off either end of the dial, and which end. */
enum class DialFit { InRange, TooDark, TooBright }

fun shutterFit(seconds: Double, scale: List<ShutterSpeed> = ShutterScale): DialFit = when {
    seconds > scale.last().seconds -> DialFit.TooDark
    seconds < scale.first().seconds -> DialFit.TooBright
    else -> DialFit.InRange
}

/**
 * A lens cannot open past its widest, so an ideal f-number below the ring means
 * the scene is too dark to shoot at the speed you locked.
 */
fun apertureFit(value: Double, scale: List<Double> = ApertureScale): DialFit = when {
    value < scale.first() -> DialFit.TooDark
    value > scale.last() -> DialFit.TooBright
    else -> DialFit.InRange
}

/**
 * The setting you hold still. Whichever one you lock, the meter solves for the
 * other - lock 1/500 and it hands back an f-stop, lock f/2.8 and it hands back a
 * speed.
 */
sealed interface Locked {
    data class Aperture(val value: Double) : Locked

    data class Shutter(val speed: ShutterSpeed) : Locked
}

/** The setting the meter solved for. */
sealed interface Solution {
    data class Shutter(
        val exactSeconds: Double,
        val snapped: SnappedShutter,
    ) : Solution

    data class Aperture(
        val exactValue: Double,
        val snapped: SnappedAperture,
    ) : Solution
}

/** Everything the readout needs, from one scene reading and the wheels. */
data class MeterResult(
    val ev100: Double,
    val evAtFilm: Double,
    val solution: Solution,
    val fit: DialFit,
) {
    val stopsUnder: Double
        get() = when (val answer = solution) {
            is Solution.Shutter -> answer.snapped.stopsUnder
            is Solution.Aperture -> answer.snapped.stopsUnder
        }

    val isClean: Boolean
        get() = when (val answer = solution) {
            is Solution.Shutter -> answer.snapped.isClean
            is Solution.Aperture -> answer.snapped.isClean
        }
}

fun meter(
    ev100: Double,
    filmIso: Int,
    locked: Locked,
    calibrationEv: Double = 0.0,
): MeterResult {
    val corrected = ev100 + calibrationEv
    return when (locked) {
        is Locked.Aperture -> {
            val exact = shutterSeconds(corrected, filmIso, locked.value)
            MeterResult(
                ev100 = corrected,
                evAtFilm = evAtFilmSpeed(corrected, filmIso),
                solution = Solution.Shutter(exact, snapShutter(exact)),
                fit = shutterFit(exact),
            )
        }

        is Locked.Shutter -> {
            val exact = apertureFor(corrected, filmIso, locked.speed.seconds)
            MeterResult(
                ev100 = corrected,
                evAtFilm = evAtFilmSpeed(corrected, filmIso),
                solution = Solution.Aperture(exact, snapAperture(exact)),
                fit = apertureFit(exact),
            )
        }
    }
}
