# Changelog

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
