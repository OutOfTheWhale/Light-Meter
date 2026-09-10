package com.outofthewhale.lightmeter

import kotlin.math.abs
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExposureTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(
            abs(expected - actual) < tolerance,
            "expected $expected but was $actual",
        )
    }

    private val fullDials = Dials(
        aperture = apertureScale(Step.Full),
        shutter = shutterScale(Step.Full),
    )

    private fun atAperture(ev100: Double, iso: Int, f: Double, dials: Dials = fullDials) =
        meter(ev100 = ev100, filmIso = iso, locked = Locked.Aperture(f), dials = dials)

    private fun atShutter(ev100: Double, iso: Int, label: String, dials: Dials = fullDials) =
        meter(
            ev100 = ev100,
            filmIso = iso,
            locked = Locked.Shutter(dials.shutter.first { it.label == label }),
            dials = dials,
        )

    @Test
    fun `sunny sixteen lands on the marked speed for the film speed`() {
        // The oldest sanity check in photography: bright sun is EV 15 at ISO 100,
        // and at f/16 that is the reciprocal of the film speed.
        val result = atAperture(15.0, 100, 16.0)
        val answer = assertIs<Solution.Shutter>(result.solution)
        assertEquals("1/125", answer.snapped.mark.label)
        assertClose(1.0 / 128, answer.exactSeconds)

        val fast = assertIs<Solution.Shutter>(atAperture(15.0, 400, 16.0).solution)
        assertEquals("1/500", fast.snapped.mark.label)
    }

    @Test
    fun `locking the shutter hands back an f-stop instead`() {
        // The other half of the user's question: "I want 1/500, what aperture?"
        // Sunny 16 at ISO 100 is 1/125 at f/16, so two stops faster is f/8.
        val result = atShutter(15.0, 100, "1/500")
        val answer = assertIs<Solution.Aperture>(result.solution)
        assertEquals(8.0, answer.snapped.mark)
    }

    @Test
    fun `the two priorities are inverses of each other`() {
        // Lock f/2_8, get a speed; lock that speed, get f/2_8 back.
        val speed = assertIs<Solution.Shutter>(atAperture(11.0, 400, 2.8).solution)
        val backToAperture = apertureFor(
            ev100 = 11.0,
            filmIso = 400,
            shutterSeconds = speed.exactSeconds,
        )
        assertClose(2.8, backToAperture, tolerance = 1e-9)
    }

    @Test
    fun `ev100 inverts back to the exposure the camera chose`() {
        // A meter that cannot round-trip its own arithmetic is measuring nothing.
        val time = 1.0 / 60
        val ev = ev100(exposureTimeSeconds = time, sensitivityIso = 800, aperture = 2.8)
        val back = shutterSeconds(ev100 = ev, filmIso = 800, aperture = 2.8)
        assertClose(time, back)
    }

    @Test
    fun `phone sensitivity cancels out of the scene reading`() {
        // Same scene, two different AE choices a phone might make. The point of
        // normalising to EV100 is that both describe the same light.
        val slowIso = ev100(exposureTimeSeconds = 1.0 / 100, sensitivityIso = 100, aperture = 2.0)
        val fastIso = ev100(exposureTimeSeconds = 1.0 / 800, sensitivityIso = 800, aperture = 2.0)
        assertClose(slowIso, fastIso)
    }

    @Test
    fun `doubling the film speed halves the time`() {
        val slow = shutterSeconds(ev100 = 12.0, filmIso = 200, aperture = 5.6)
        val fast = shutterSeconds(ev100 = 12.0, filmIso = 400, aperture = 5.6)
        assertClose(slow / 2, fast)
    }

    @Test
    fun `doubling the film speed closes the aperture one stop`() {
        val slow = apertureFor(ev100 = 12.0, filmIso = 200, shutterSeconds = 1.0 / 125)
        val fast = apertureFor(ev100 = 12.0, filmIso = 400, shutterSeconds = 1.0 / 125)
        assertClose(slow * kotlin.math.sqrt(2.0), fast)
    }

    @Test
    fun `stopping down one stop doubles the time`() {
        val wide = shutterSeconds(ev100 = 12.0, filmIso = 400, aperture = 4.0)
        val stopped = shutterSeconds(ev100 = 12.0, filmIso = 400, aperture = 5.6)
        // f/5.6 is nominal, so this is a hair over one stop rather than exactly one.
        assertTrue(abs(stopped / wide - 2.0) < 0.05, "expected about 2x, was ${stopped / wide}")
    }

    @Test
    fun `calibration shifts the reading by whole stops`() {
        val plain = assertIs<Solution.Shutter>(atAperture(12.0, 100, 8.0).solution)
        val darker = assertIs<Solution.Shutter>(
            meter(
                ev100 = 12.0,
                filmIso = 100,
                locked = Locked.Aperture(8.0),
                dials = fullDials,
                calibrationEv = -1.0,
            ).solution,
        )
        // One stop less measured light means one stop more time on the dial.
        assertClose(plain.exactSeconds * 2, darker.exactSeconds)
    }

    @Test
    fun `snapping reports which way the marked speed misses`() {
        // Ideal time longer than the mark: the mark lets in less light, so it underexposes.
        val underexposing = snapShutter(1.0 / 90, shutterScale(Step.Full))
        assertEquals("1/125", underexposing.mark.label)
        assertTrue(underexposing.stopsUnder > 0, "1/125 is faster than 1/90, so it underexposes")
        assertTrue(!underexposing.isClean)

        val exact = snapShutter(1.0 / 125, shutterScale(Step.Full))
        assertEquals("1/125", exact.mark.label)
        assertTrue(exact.isClean)
    }

    @Test
    fun `snapping an aperture flips the sign the other way`() {
        // A wider mark than ideal lets in more light, so it overexposes.
        val wider = snapAperture(6.5, apertureScale(Step.Third))
        assertEquals(6.3, wider.mark)
        assertTrue(wider.stopsUnder < 0, "f/6.3 is wider than f/6.5, so it overexposes")

        val exact = snapAperture(8.0, apertureScale(Step.Third))
        assertEquals(8.0, exact.mark)
        assertTrue(exact.isClean)
    }

    @Test
    fun `light beyond the shutter dial is reported rather than snapped silently`() {
        // Moonlight at f/16 on slow film: far past 30 seconds.
        assertEquals(DialFit.TooDark, atAperture(-4.0, 50, 16.0).fit)
        // Absurdly bright at f/1_4 on fast film: past 1/8000.
        assertEquals(DialFit.TooBright, atAperture(17.0, 3200, 1.4).fit)
        assertEquals(DialFit.InRange, atAperture(12.0, 400, 8.0).fit)
    }

    @Test
    fun `light beyond the aperture ring is reported too`() {
        // No lens opens past f/1_0, so a dark room at 1/1000 is unshootable.
        assertEquals(DialFit.TooDark, atShutter(2.0, 100, "1/1000").fit)
        // Bright sun on a long exposure would need a pinhole beyond f/45.
        assertEquals(DialFit.TooBright, atShutter(15.0, 400, "1s").fit)
        assertEquals(DialFit.InRange, atShutter(12.0, 400, "1/125").fit)
    }
}

class ScalesTest {

    @Test
    fun `every scale is ordered at every increment`() {
        Step.entries.forEach { step ->
            assertEquals(isoScale(step).sorted(), isoScale(step), "iso $step")
            assertEquals(apertureScale(step).sorted(), apertureScale(step), "aperture $step")
            val seconds = shutterScale(step).map { it.seconds }
            assertEquals(seconds.sorted(), seconds, "shutter $step")
        }
    }

    @Test
    fun `coarser increments are strict subsets of finer ones`() {
        // A full stop has to be the same f/8 whichever wheel you reached it on.
        assertTrue(apertureScale(Step.Third).containsAll(apertureScale(Step.Full)))
        assertTrue(isoScale(Step.Third).containsAll(isoScale(Step.Full)))
        assertTrue(
            shutterScale(Step.Third).map { it.label }
                .containsAll(shutterScale(Step.Full).map { it.label }),
        )
        assertTrue(
            shutterScale(Step.Half).map { it.label }
                .containsAll(shutterScale(Step.Full).map { it.label }),
        )
    }

    @Test
    fun `each increment really is that many stops apart`() {
        // Individual marks are rounded for printing - f/1.2 stands in for f/1.189 -
        // so a single gap can be a tenth of a stop out and still be right. The mean
        // gap cannot: it pins the whole scale, and separates thirds from halves from
        // full stops with room to spare.
        fun meanGap(values: List<Double>): Double =
            values.zipWithNext { a, b -> log2(b / a) }.average()

        listOf(Step.Full to 1.0, Step.Half to 0.5, Step.Third to 1.0 / 3).forEach { (step, stops) ->
            // Exposure goes as the square of the f-number, so squares give stops.
            val aperture = meanGap(apertureScale(step).map { it * it })
            assertTrue(abs(aperture - stops) < 0.03, "aperture $step averaged $aperture")

            val iso = meanGap(isoScale(step).map { it.toDouble() })
            assertTrue(abs(iso - stops) < 0.03, "iso $step averaged $iso")

            val shutter = meanGap(shutterScale(step).map { it.seconds })
            assertTrue(abs(shutter - stops) < 0.03, "shutter $step averaged $shutter")
        }
    }

    @Test
    fun `changing increment keeps the value and finds the nearest mark`() {
        // ISO 640 is a third-stop speed; in full stops the nearest is 800, not 400,
        // because nearness is measured in stops rather than by subtraction.
        assertEquals(800, nearestIso(isoScale(Step.Full), 640))
        assertEquals(400, nearestIso(isoScale(Step.Full), 500))
        assertEquals(400, nearestIso(isoScale(Step.Full), 400))

        assertEquals(8.0, nearestAperture(apertureScale(Step.Full), 7.1))
        assertEquals(5.6, nearestAperture(apertureScale(Step.Full), 6.3))

        assertEquals("1/125", nearestShutter(shutterScale(Step.Full), 1.0 / 100).label)
        assertEquals("1/60", nearestShutter(shutterScale(Step.Full), 1.0 / 50).label)
    }

    @Test
    fun `the answer lands on a mark the chosen increment offers`() {
        // A body with full-stop detents must never be told to use f/7.1.
        val full = meter(
            ev100 = 12.3,
            filmIso = 400,
            locked = Locked.Shutter(ShutterSpeed(1.0 / 500, "1/500")),
            dials = Dials(apertureScale(Step.Full), shutterScale(Step.Full)),
        )
        assertTrue(assertIs<Solution.Aperture>(full.solution).snapped.mark in apertureScale(Step.Full))

        val third = meter(
            ev100 = 12.3,
            filmIso = 400,
            locked = Locked.Shutter(ShutterSpeed(1.0 / 500, "1/500")),
            dials = Dials(apertureScale(Step.Third), shutterScale(Step.Third)),
        )
        assertTrue(assertIs<Solution.Aperture>(third.solution).snapped.mark in apertureScale(Step.Third))
    }

    @Test
    fun `apertures read the way a lens barrel is engraved`() {
        assertEquals("f/8", formatAperture(8.0))
        assertEquals("f/1.4", formatAperture(1.4))
        assertEquals("f/5.6", formatAperture(5.6))
    }

    @Test
    fun `durations read the way a shutter dial is engraved`() {
        assertEquals("1/125", formatSeconds(1.0 / 125))
        assertEquals("1/90", formatSeconds(1.0 / 90))
        assertEquals("1/1000", formatSeconds(1.0 / 1000))
        assertEquals("2s", formatSeconds(2.0))
        assertEquals("1.5s", formatSeconds(1.5))
        assertEquals("2min", formatSeconds(120.0))
        assertEquals("--", formatSeconds(0.0))
    }

    @Test
    fun `calibration reads as signed stops`() {
        assertEquals("0", formatCalibration(0))
        assertEquals("+1", formatCalibration(3))
        assertEquals("-1", formatCalibration(-3))
        assertEquals("+0.3", formatCalibration(1))
    }
}
