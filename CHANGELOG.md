# Changelog

## 0.1.0-beta.7 — 2026-11-07

- **Retune — stage 1 of the voice plan ships.** A new
  **🎯 Retune…** button corrects the selected pad's singing to
  a scale, the way the famous pitch-correction tools do it,
  but offline and with the singer holding the dial: pick a key
  (C through B), a scale (Major, Minor, or Chromatic), and a
  strength from a gentle nudge to a full snap. The app's own
  pitch tracker reads the take frame by frame, and a granular
  pitch shifter re-renders it at the corrected pitch. It always
  starts from your original take, so you can re-run it at any
  strength — the natural take is never lost. Stages 2 and 3 of
  the plan (calibration, your voice as an instrument) build on
  this shifter.
- Per the release policy, beta.5 retires with this release:
  beta.7 (current) and beta.6 (fallback) are the two live
  versions.

## 0.1.0-beta.6 — 2026-11-07

- **The instructions are back ON the pads — and they fit.**
  Paul's word, after a night with both versions: people need to
  read how to drive a pad right on the pad. So each pad now
  shows its number big, then *"Tap to record · tap to stop /
  Hold to clear"* in small type, sized and spaced to sit whole
  inside the pad — the beta.4 labels clipped, the beta.5
  numbers-only pads told you nothing; this is the middle Paul
  actually asked for twice. State still reads from color, the
  breathing pulse, and the play-together row.
- Per the release policy, beta.4 retires with this release:
  beta.6 (current) and beta.5 (fallback) are the two live
  versions.

## 0.1.0-beta.5 — 2026-11-07

Two fixes from Paul's first hands-on with beta.4:

- **Pads are numbers now.** The pads carried two or three lines
  of state text under the number, and on Paul's phone the bottom
  line was cut off. At his direction the pads show just **1–6**,
  big. State is still visible at a glance — the colors, the
  breathing pulse on a playing pad, and the numbered
  play-together row all still speak.
- **Chant mode, hardened.** On Paul's phone "Build & play"
  appeared to do nothing. Three changes: the rendered chant is
  half the length (16 beats — the size of an ordinary pad take,
  which phones loop reliably); building a chant now stops the
  six pads first so the chant is heard on its own, unmistakably;
  and if there aren't two recorded pads to build from, the
  status line now says exactly how many it found.

Also this cycle: **beta.1 was retired from GitHub** (release,
file and tag removed at Paul's direction). Its one-off signing
key is gone, so it could never update cleanly; everything from
beta.2 onward shares the kept key and installs over the top.

## 0.1.0-beta.4 — 2026-11-07

Two more of Paul's rules, set during the beta.3 field test:

- **The words now fit the music.** In beta.3 a phrase started on
  its beat but kept its natural length — long phrases spilled,
  short ones left gaps. Now every phrase is digitally stretched
  or squeezed to exactly one beat before it is placed, anchor
  included. The grid wins; nobody's pace survives.
- **Four instrumental styles**, described rather than named for
  any artist, each a pulse-and-voice recipe the user pours their
  own words into: **Robot Chant** (four-on-the-floor pulse,
  ring-modulated voice, 120 BPM), **Acid House** (driving kick
  plus a squelchy synthesized bass arp, 124), **Industrial
  Drive** (driven kicks, metallic hits, a low drone, crushed
  voice, 100), **Dark Synth** (kick-and-snare groove with a
  pulsing bass, voice left clean, 105). Pick from the Chant
  dialog; the tempo follows the style until you move it.

## 0.1.0-beta.3 — 2026-11-07

- **Chant mode — the first music-creation template.** Built from
  Paul's CHANT-TEMPLATE design the same evening he drew it up:
  imitate the *mechanism* of the classic robot-chant records —
  a steady electronic pulse, a processed voice chanting short
  phrases one to a beat — while every word stays the user's own.
  Record phrases onto pads, tap **🤖 Chant…**, choose a tempo
  (90–140 BPM), an anchor pad whose phrase lands every four
  beats as the refrain, the synthesized pulse and the
  ring-modulated robot voice, and the app renders one chanting
  loop: first pass runs half the phrases, second pass runs them
  all. Stop all stops the chant too. The template ships empty;
  there is no preset lyric sheet and no borrowed hook.

## 0.1.0-beta.2 — 2026-11-07

First field fixes, from the first real phone test (Paul, Moto G,
same evening beta.1 shipped):

- **The instructions now say it out loud.** A permanent line under
  the title reads "Tap a pad to record · Hold a pad to clear it" —
  beta.1 only whispered "hold to clear" in small print on a filled
  pad, and the first tester had to discover it by accident.
- **"Play together" number row.** Six big numbered toggles under
  Play all / Stop all: tap a number to add that pad to what's
  playing, tap it again to drop it back out. Play 1 + 4, add 3,
  drop 4, hold a pad to clear and re-record it — building a piece
  up and down live, instead of only all-or-one.

## 0.1.0-beta.1 — 2026-11-07

First public beta. **Primary artifact: the native Android app**
(`android/`, package `com.damonini.wovenoak`), shipped as a
sideloadable APK on the GitHub release.

**Named on release day:** the instrument is **Woven Oak** — woven
acapella — joining WovenCut in the Woven family (working title
during development was LoopBoard; package and repository renamed
accordingly). Companion repository **woven-oak-sounds** created the
same day: a curated archive of truly remarkable sounds, gated by an
actual listen.

### Android app

- **Loop export / gifting:** any pad's loop can be saved as a WAV
  file through the system save dialog and handed onward through the
  share sheet — *creation is the best gift*.

- Six loop pads in a phone-first 2 × 3 grid, portrait, screen kept on.
- Two-tap looping, exactly as designed: tap to record, tap again to
  stop the take and start the loop playing automatically.
- Tap a playing pad to stop it; tap again to replay; hold to clear a
  pad; **Play all** and **Stop all** master controls — every pad with
  a loop plays together as one piece.
- **Built-in sound library — 30 synthesized instrument voices**
  (piano, guitars, strings, winds, brass, harp, organ, synth,
  harmonica, accordion, folk strings, mallets, erhu, sitar, bells,
  drums). Each voice is a preset of harmonics + ADSR + vibrato
  rendered by one synth engine — "in the character of" the
  instrument, not a recording of it. Load a voice's phrase onto a
  pad without recording anything.
- **Hum-to-instrument:** record a hum on a pad, then re-voice it in
  any of the 29 melodic built-in voices. The take is pitch-tracked
  on-device (autocorrelation), its melody extracted and
  re-synthesized in the chosen timbre at the take's original length.
  Drums are excluded from hum choices (percussion has no pitch);
  they remain in the library.
- **Live pitch display:** while you hum or sing into a pad, the
  note you are singing appears live (e.g. A#3, C4), with a
  sharp/flat drift meter in cents, and your session vocal range
  ("Your range: C3 – A4") tracked from the notes you have sung.
  Sharps by default; a ♯/♭ toggle switches to flat spellings.
- Latency approach, native edition: raw PCM capture via `AudioRecord`;
  playback via `AudioTrack` in MODE_STATIC with `setLoopPoints`
  spanning the whole take — a true gapless loop restarted by the
  audio engine itself.
- One pad records at a time (one microphone); takes cap at 2 minutes.
- Playing pad breathes (alpha pulse) with a period equal to its own
  loop length; recording pad is red with a live elapsed-time readout.
- RECORD_AUDIO is the only permission; plain, friendly denial message
  explaining recordings never leave the device.
- Framework Android + Kotlin stdlib only. No accounts, no telemetry,
  no ads. GPL v3.

### Browser version (secondary)

- The earlier single-file web prototype is kept in `web/` as a
  secondary artifact: six pads, same two-tap model, buffer-based
  Web Audio looping (`AudioBufferSourceNode`, `loop=true`). Not the
  focus of this release.
