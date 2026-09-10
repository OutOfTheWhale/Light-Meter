# Light Meter

A reflected-light meter for film photography, built for the [Light Phone III](https://www.thelightphone.com/).

Point the phone at your scene. Set the film speed on the ISO wheel, lock either
the aperture or the shutter speed, and the meter gives you the other one.

There is no viewfinder. You point the phone and read the number, the way you
would a handheld meter — the camera runs headless and its frames are discarded
the moment they arrive.

It is a plain Android app, not a Light SDK tool — see [Why not the SDK](#why-not-the-sdk).
It is styled to sit beside the built-in tools: black ground, one grey, and
Akkurat, which it loads from the LP3's system fonts rather than bundling.

## How it reads the light

There is no light sensor involved. The trick is not to fight the camera: left in
auto, the phone's auto-exposure loop already solves for a correctly exposed frame
and publishes its answer — exposure time, sensitivity, f-number — in every
`CaptureResult`. That triplet *is* a measurement of the scene.

Normalising it to ISO 100 gives EV100, a number with nothing left in it that is
specific to the phone:

```
EV100 = log2(N² / t) − log2(S / 100)
```

From there it is ordinary exposure arithmetic. Lock the aperture and it solves
for time; lock the shutter and it solves for the f-number:

```
t = N² / 2^(EV100 + log2(S_film / 100))
N = √( t · 2^(EV100 + log2(S_film / 100)) )
```

No pixels are inspected, nothing is captured to disk, and nothing leaves the
phone — the app requests no network permission at all. The capture session still
needs somewhere to put its frames, so it gets a small `ImageReader` whose images
are closed the instant they arrive.

## Using it

| Control | What it does |
| --- | --- |
| **ISO wheel** | Film speed, 12–6400 |
| **Second wheel** | Whichever setting you locked — apertures or shutter speeds |
| **LOCK** | Which setting you hold still — APERTURE or SHUTTER. The current exposure carries across, so the reading doesn't jump |
| **READING** | LIVE or HELD. Held freezes the reading, so you can look away from the scene to set your camera |
| **LENS** | REAR or FRONT — the lens currently metering |
| **Gear** | Increments and calibration |

The big number is the answer, and it is the only thing on the upper half of the
screen — the film speed and the locked setting are already on the wheels below,
so repeating them would only crowd the number you came to read. `LOW` and `HIGH`
mean the scene has fallen off the end of the dial entirely.

### Increments

Each dial can run in full stops, halves or thirds, set independently under the
gear. This changes the numbers the wheels offer *and* the numbers the meter will
answer with: put the aperture on full stops and it will never tell you f/7.1,
because a body with full-stop detents has no way to accept it.

Values survive a change of increment. Switching ISO from thirds to full stops
moves 640 to 800 rather than 400 — nearness is measured in stops, not by
subtraction — and switching back leaves it on 800.

The scales are the conventional printed ones rather than computed powers of two,
so half-stop film speeds read 140, 280, 560 as they do on a camera, not 141, 283,
566.

### Calibrating it

Open the gear and dial until it agrees with a meter you trust.
Phone auto-exposure targets a rendering, not a fixed reflectance, so expect to
need a fraction of a stop. The setting persists.

### Nominal vs exact

The scales carry the numbers actually engraved on a lens barrel and a shutter
dial, not their exact geometric equivalents — f/1.2 is really f/1.189, and a
marked "1/60" fires for about 1/64s. Metering against the nominal number is what
every handheld meter does, and it keeps the readout honest: what the app prints
is what you dial in. Worst-case error from this is about a tenth of a stop, well
inside film's latitude.

## Building

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew :app:testDebugUnitTest` runs the exposure tests — pure JVM, covering
both priority directions, the EV round trip, and the dial limits.

## Why not the SDK

This started as a Light SDK tool and could not stay one. The SDK sandbox blocks
tools from importing `android.content.Context` or calling `getSystemService()`,
which seals off `CameraManager`; the only camera surface it offers is a QR
scanner that returns a decoded string. There is no sanctioned way for a tool to
read the exposure metadata a light meter needs.

Patching the sandbox was not enough either. LightOS verifies callers at runtime
before issuing a service token, and at the default `AllowLightApprovedApks`
filter level it rejects every sideloaded tool — including ones built entirely
within the sandbox:

```
LightSdkService: Rejected GetToken from unverified caller uid=...
```

A plain Android app sidesteps both. It never talks to the SDK server, so there is
no token to be refused, and it uses `android.hardware.camera2` directly with an
ordinary runtime permission. The cost is that it does not appear in the LightOS
tool list the way an SDK tool does.

## Credits

The gear icon and the colour and type tokens come from the
[Light SDK](https://github.com/lightphone/light-sdk), MIT licensed, copyright
The Light Phone. Akkurat is not bundled — it is read from the LP3's own system
fonts at runtime.
