# Light Meter

A reflected-light meter for film photography, built for the
[Light Phone III](https://www.thelightphone.com/).

Set the film speed. Lock either the aperture or the shutter. Point the phone at
your scene and read the other one off the screen.

There is no viewfinder, by design. You point the phone and read the number, the
way you would a handheld meter — the camera runs headless and throws its frames
away the moment they arrive.

## The screen

One number fills the upper half: the setting you did *not* lock. Nothing else
competes with it, because the film speed and the locked setting are already on
the wheels a thumb's width below.

```
                                    ⚙

                 1/125

        ISO                 APERTURE
        320                   f/7.1
      ┌─ 400 ─┐            ┌─ f/8 ─┐
        500                   f/9

     LOCK        READING        LENS
   APERTURE       LIVE          REAR
```

`LOW` and `HIGH` replace the number when the scene has fallen off the end of the
dial — too dark to shoot at the speed you locked, or too bright to stop down far
enough.

## The wheels

Two detented wheels, three rows tall, the middle row selected. They report as
your thumb passes each detent, so the answer moves with the wheel rather than
waiting for it to settle.

The left wheel is always film speed, **ISO 12–6400**. The right wheel is
whichever setting you locked. The setting you did not lock has no wheel — it is
the answer, and a wheel there would only invite you to argue with the meter.

## The controls

Each names a thing and shows its state beneath.

| Control | States | What it does |
| --- | --- | --- |
| **LOCK** | `APERTURE` · `SHUTTER` | Which setting you hold still. The meter solves for the other |
| **READING** | `LIVE` · `HELD` | Held freezes the reading so you can lower the phone and set your camera |
| **LENS** | `REAR` · `FRONT` | Which lens is metering |
| **⚙** | — | Increments and calibration |

Switching **LOCK** carries the current exposure across. If it was reading 1/125
at f/8, locking the shutter instead leaves you on 1/125 answering f/8 — the
exposure does not jump just because you changed your mind about which dial you
are willing to move.

**HELD** sets an auto-exposure lock on the camera, so the reading is genuinely
frozen rather than merely paused on screen. It clears itself whenever you return
to the app, on the grounds that a stale reading from another room is worse than
no reading.

## Increments

Each dial runs in **full stops, halves or thirds**, set independently under the
gear. This changes the numbers the wheels offer *and* the numbers the meter will
answer with: put the aperture on full stops and it will never tell you f/7.1,
because a body with full-stop detents has no way to accept it.

Values survive a change of increment. Switching ISO from thirds to full stops
moves 640 to **800**, not 400 — nearness is measured in stops, not by
subtraction — and switching back leaves it on 800.

The scales are the conventional printed series rather than computed powers of
two, so half-stop film speeds read 140, 280, 560 as they do on a camera, not
141, 283, 566.

## Calibration

Plus or minus three stops in thirds, under the gear. Meter something against a
meter you trust and dial until they agree; the setting persists.

Expect to need a fraction of a stop. Phone auto-exposure targets a pleasing
rendering rather than a fixed 18% reflectance, so the raw reading carries a small
built-in bias that is consistent and therefore correctable.

## How it reads the light

There is no light sensor involved, and no pixel is ever examined.

The trick is not to fight the camera. Left in auto, the phone's auto-exposure
loop already solves for a correctly exposed frame and publishes its answer —
exposure time, sensitivity, f-number — in every `CaptureResult`. That triplet
*is* a measurement of the scene.

Normalising it to ISO 100 gives EV100, a number with nothing phone-specific left
in it:

```
EV100 = log2(N² / t) − log2(S / 100)
```

From there it is ordinary exposure arithmetic. Lock the aperture and it solves
for time; lock the shutter and it solves for the f-number:

```
t = N² / 2^(EV100 + log2(S_film / 100))
N = √( t · 2^(EV100 + log2(S_film / 100)) )
```

Readings are taken only once auto-exposure reports itself converged, and any
post-raw sensitivity boost is folded in, since the camera counts that gain as
part of the exposure too. Small corrections are smoothed so the number does not
shiver; a real change of scene is followed at once.

The capture session still needs somewhere to put its frames, so it gets a small
`ImageReader` whose images are closed the instant they arrive. Nothing is written
to disk, and the app requests no network permission at all.

### Nominal versus exact

The scales carry the numbers actually engraved on a lens barrel and a shutter
dial, not their exact geometric equivalents — f/1.2 is really f/1.189, and a
marked "1/60" fires for about 1/64s.

Metering against the nominal number is what every handheld meter does, and it
keeps the readout honest: what the app prints is what you can dial in. The
worst-case error is about a tenth of a stop, well inside film's latitude.

## Installing

Build one, or take an APK from the releases:

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

It is a plain Android app rather than a Light SDK tool, so it does not appear in
the LightOS tool list. Launch it from the phone's app list, or:

```
adb shell am start -n com.outofthewhale.lightmeter/.MainActivity
```

### Release builds

Put a `keystore.properties` at the repo root pointing at your own signing key:

```
storeFile=keystore/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Both it and `keystore/` are gitignored — a public repository is no place for a
release key. Without them the project still builds; the release APK just comes
out unsigned. `./gradlew :app:assembleRelease` produces about 1.9 MB against the
debug build's 24 MB.

## Tests

```
./gradlew :app:testDebugUnitTest
```

The exposure maths is pure JVM code and is tested as such: sunny 16, the EV round
trip, the two priority directions being exact inverses of each other, calibration
shifting by whole stops, which way a snapped mark misses, the ends of each dial,
and — for every increment — that the scales are ordered, that coarser ones are
subsets of finer ones, that mean spacing matches the stated fraction, and that
the answer always lands on a mark the chosen increment offers.

## Why not a Light SDK tool

Briefly, because it cannot be one. The SDK sandbox blocks tools from importing
`android.content.Context` or calling `getSystemService()`, which seals off
`CameraManager`; its only camera surface is a QR scanner that returns a decoded
string. Nothing there exposes the exposure metadata a light meter needs.

Patching the sandbox is not enough either — LightOS verifies callers at runtime
before issuing a service token, and at its default filter level it refuses every
sideloaded tool regardless of how it was built.

A plain Android app sidesteps both. It never talks to the SDK server, so there is
no token to be refused, and it uses `android.hardware.camera2` directly with an
ordinary runtime permission.

## Credits

The colour and type tokens come from the
[Light SDK](https://github.com/lightphone/light-sdk), MIT licensed, copyright
The Light Phone. Akkurat is not bundled — it is read from the LP3's own system
fonts at runtime, so the app matches the built-in tools without shipping anyone
else's typeface.
