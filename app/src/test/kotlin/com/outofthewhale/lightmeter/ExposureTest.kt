package com.outofthewhale.lightmeter

import kotlin.math.abs
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

    private fun atAperture(ev100: Double, iso: Int, f: Double) =
        meter(ev100 = ev100, filmIso = iso, locked = Locked.Aperture(f))

    private fun atShutter(ev100: Double, iso: Int, label: String) =
        meter(
            ev100 = ev100,
            filmIso = iso,
            locked = Locked.Shutter(ShutterScale.first { it.label == label }),
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
                calibrationEv = -1.0,
            ).solution,
        )
        // One stop less measured light means one stop more time on the dial.
        assertClose(plain.exactSeconds * 2, darker.exactSeconds)
    }

    @Test
    fun `snapping reports which way the marked speed misses`() {
        // Ideal time longer than the mark: the mark lets in less light, so it underexposes.
        val underexposing = snapShutter(1.0 / 90)
        assertEquals("1/125", underexposing.mark.label)
        assertTrue(underexposing.stopsUnder > 0, "1/125 is faster than 1/90, so it underexposes")
        assertTrue(!underexposing.isClean)

        val exact = snapShutter(1.0 / 125)
        assertEquals("1/125", exact.mark.label)
        assertTrue(exact.isClean)
    }

    @Test
    fun `snapping an aperture flips the sign the other way`() {
        // A wider mark than ideal lets in more light, so it overexposes.
        val wider = snapAperture(6.5)
        assertEquals(6.3, wider.mark)
        assertTrue(wider.stopsUnder < 0, "f/6.3 is wider than f/6.5, so it overexposes")

        val exact = snapAperture(8.0)
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
    fun `scales are ordered and hold their defaults`() {
        assertEquals(IsoScale.sorted(), IsoScale)
        assertEquals(ApertureScale.sorted(), ApertureScale)
        assertEquals(ShutterScale.map { it.seconds }.sorted(), ShutterScale.map { it.seconds })
        assertEquals(400, IsoScale[DefaultIsoIndex])
        assertEquals(8.0, ApertureScale[DefaultApertureIndex])
        assertEquals("1/125", ShutterScale[DefaultShutterIndex].label)
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
