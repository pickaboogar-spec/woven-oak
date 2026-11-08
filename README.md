# Woven Oak

**Status: ROUGH BETA** (v0.1.0-beta.1) — this is a rough beta: expect
rough edges. Feedback welcome.

## Testers wanted

This beta was built and checked on one setup. Android phones vary —
just because it works on our hardware doesn't mean it will work on
everybody's hardware, and outside opinions are how it gets better.
If you try it, please open an Issue and tell us:

- your phone model,
- your Android version,
- what worked — or what broke.

Thank you for testing.

**A truly electronic acapella instrument.** Six pads. Sing, hum,
beatbox or clap a short phrase into a pad and it loops — layer the six
pads and you are your own choir, band and rhythm section.

*Woven Oak* — woven acapella, woven from your own voice. It joins
WovenCut in the Woven family of free software from Damonini and
Pepper Software Development.

Free forever. No paywall, no accounts, no telemetry, no ads.
Your recordings are made and played entirely on your own device —
nothing is ever uploaded anywhere.

**Permissions (current version, beta.10):** exactly two. The
**microphone**, only to record your loops on the device. And
**internet**, used for exactly one thing: on launch, one small
request to GitHub asking whether a newer Woven Oak has been
published, so the app can offer you the update. That check is
the app's only network use — no analytics, no telemetry, no
other endpoints — and everything else in the app works fully
offline. (Versions through beta.9 asked for the microphone
only.)

## The app (Android)

Woven Oak is a native Android app: [`android/`](android/) in this
repo, package `com.damonini.wovenoak`. The beta ships as a
sideloadable APK attached to the GitHub release.

**Install the beta APK:** download `WovenOak-0.1.0-beta.1.apk` from
the release, open it on your phone, and allow the install. Android
may warn you that the file comes from outside the Play Store and
that it is unsigned-by-a-store / debug-signed — that is normal for a
beta like this; the app itself asks for exactly one permission, the
microphone, and uses it only to record your loops on the device.

**Requires:** Android 8.0 (API 26) or newer.

### Built-in sounds: 30 synthesized instrument voices

No recording needed — tap **♪ Sounds…**, pick a voice, and its phrase
loops on the last pad you tapped. Every voice is **synthesized by the
app itself** (harmonic recipe + envelope + vibrato, rendered live) —
an approximation *in the character of* the instrument, never a
recording of one, and so completely license-clean:

Piano · Acoustic Guitar · Electric Guitar · Bass Guitar · Violin ·
Viola · Cello · Double Bass · Flute · Clarinet · Oboe · Bassoon ·
Saxophone · Trumpet · Trombone · French Horn · Harp · Organ ·
Synthesizer · Harmonica · Accordion · Ukulele · Banjo · Mandolin ·
Xylophone · Marimba · Erhu · Sitar · Bells · Drums

### Hum it, hear it as an instrument

Hum or sing a melody into a pad, then tap **🎤 Hum → instrument…**
and pick a voice: the app pitch-tracks your take **on the phone**
(autocorrelation melody extraction), and re-plays your melody in
that instrument's character, looping at the take's original length.
Re-voice the same hum as a flute, then a cello, then a trumpet —
your original hum is kept until you clear the pad. Drums are the one
voice not offered here: percussion has no pitch for a hum to map
onto. (A future version might explore AI-assisted conversion; this
beta is fully offline by design.)

### See your pitch

While you record, the note you are singing shows live above the
pads — **A#3**, **C4** — with a needle showing how many cents sharp
or flat you are drifting, and your **session vocal range** builds up
underneath ("Your range: C3 – A4") so you can see where your voice
lives. Note names default to sharps; the **♯/♭** button switches to
flat spellings.

### Give a loop away

Creation is the best gift. Tap **⇪ Export loop as a WAV gift…** and
the last pad you tapped is saved as a standard WAV file — then the
share sheet opens so you can hand it straight to another musician
to build on. Loops are beginnings, not endings.

### How to play

1. Open Woven Oak and allow microphone access when asked. The mic
   is only used to record your loops, on your phone.
2. **Tap a pad** — recording starts.
3. **Tap it again** — recording stops and the loop starts playing
   automatically, looping on its own.
4. **Tap a playing pad** to stop it. Tap it again to play it again.
5. **Hold a pad** to clear it and record a new one.
6. **Play all** starts every loaded pad together; **Stop all**
   silences every pad at once.
7. **Sounds…**, **Hum → instrument…** and **Export…** act on the
   last pad you tapped.

Each pad loops its own phrase at its own length — no fixed tempo grid
in this beta, so your loops layer freely, the way voices do. One pad
records at a time; a take stops itself at two minutes.

### Why a phone can feel like a hardware board

A hardware looper answers the instant you touch it; a phone has to
earn that feeling. Woven Oak closes the gap the native way: takes
are captured as raw PCM (`AudioRecord`) and played back through an
`AudioTrack` in static mode with its loop points set across the
whole take — the loop restarts **inside the audio engine**,
gaplessly, with no app-code scheduling in the loop path. The pads
are big thumb targets filling the screen, and a playing pad breathes
in time with its own loop, so your eyes get the response your
fingers would feel on hardware.

### Build it yourself

```sh
cd android
gradle assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
```

Toolchain: JDK 17, Gradle 8.9, Android SDK 34 (AGP 8.5.2, Kotlin
2.0.20). Framework Android + Kotlin stdlib only — no AndroidX, no
Compose, no third-party dependencies.

> Maintainer note: the 0.1.0-beta.1 release APK was produced in
> Pepper's environment with the equivalent manual toolchain
> (aapt2 → kotlinc → d8 → zipalign → apksigner, debug key) because
> the Gradle daemon cannot run in that sandbox; the Gradle build
> above is the canonical path and describes the same app, package
> and version. The shipped APK's badging was verified with aapt:
> package `com.damonini.wovenoak`, versionName `0.1.0-beta.1`,
> minSdk 26, targetSdk 34, RECORD_AUDIO the only permission.

## Browser version (secondary)

An early single-file web prototype lives in [`web/`](web/) — open
`web/index.html` in a mobile browser (https or localhost required for
the microphone). It is a secondary artifact and not the focus of this
release; the Android app above is the instrument.

## Roadmap

- **Woven Oak Sounds** — a curated companion repository
  (`woven-oak-sounds`, a sibling of this repo): an archive of truly
  remarkable recorded sounds. It is *discerning by design*: every
  submitted sound is actually listened to — by a human or an AI
  listener — and only a sound judged truly unique is kept, credited
  to its recorder and shared under CC BY 4.0. The gift flow above
  (export a loop as WAV, share it onward) is how sounds travel from
  this app toward that archive; an in-app submission flow is future
  work — tonight the repository, its curation policy, and the export
  path exist.
- Beat-quantised loop lengths and simple per-pad effects.
- Session save/recall of all six pads.

## Version

**0.1.0-beta.1** — first public beta. See [CHANGELOG.md](CHANGELOG.md).

## License

Woven Oak is free software under the **GNU General Public License
v3** (GPL v3) — see [LICENSE](LICENSE) and [COPYRIGHT](COPYRIGHT).

Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper — Damonini
and Pepper Software Development.

## Donations

Woven Oak is free for everybody, always. If it brings you joy and
you would like to say thanks, donations are appreciated but never
expected and never unlock anything — the tip jar is by the door,
the music is free.
