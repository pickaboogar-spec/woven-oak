# Woven Oak — Voice-as-Instrument: the exact build plan
## Grounded in the code as it stands at beta.6 (2026-11-07)

Paul asked for the figured-out version of VOICE-DESIGN.md.
Here it is, stage by stage, against what the app already has.

### Already in the app (the foundation)
- `Pitch.kt` — `PitchTracker.analyse()` reads pitch + cents off
  a live frame; `extractMelody()` turns a whole take into
  `NoteEvent`s (note, start, duration). Detection: done.
- `Hum.kt` — already converts a hummed take into synthesized
  instruments from its pitch contour. The voice instrument is
  this same trick with the samples swapped: your voice instead
  of a synth voice.
- `Chant.kt` — offline rendering onto a beat grid into one PCM
  buffer, looped by a pad. The voice instrument renders the
  same way.
- `LoopPad.kt` — every take is raw PCM at 44.1kHz, loadable and
  loopable. Samples are just takes with names.

### Stage 1 — Retune (new file: `Retune.kt`)
Take a recorded pad. Analyse it in ~10ms frames. For each
frame: sung note → nearest note in the chosen scale → shift
ratio 2^(cents/1200), scaled by a Strength dial (0 = natural,
100% = full Auto-Tune-style snap) and smoothed over a Speed
window so corrections glide instead of jumping. Pitch-shift by
windowed resampling with overlap-add (time-domain, keeps the
voice's character at small shifts). Render into a pad like the
chant does. **This is the only stage needing a new algorithm,
and it is a known one.**

### Stage 2 — Calibration (new flow + storage)
- The app sings first: a guide tone (synth voice from
  `Instruments.kt`), starting from a note the user picks by
  singing anything comfortable.
- It walks down a semitone at a time, then up, playing each
  guide note; the user sings it back on "ahh" for ~2 seconds.
  A note is *accepted* when the tracker holds it steady
  (low cents wobble); the walk stops at the user's tap or when
  steadiness fails. Accepted extremes = the range map.
- From each accepted take, keep the steadiest 1 second as the
  note's sample. Storage: raw PCM in app-private files, one per
  MIDI note, plus a tiny index. ~88KB a note; a 25-note range
  is ~2MB. It never leaves the device — there is still exactly
  one permission, the microphone.

### Stage 3 — Your voice, playable (new voice in the picker)
"Your voice" joins the instrument list. To sound a note: take
the nearest stored sample (in practice within a few semitones),
shift it with Stage 1's shifter, loop its steady middle for
the note's length. Melodies come from a hummed take (via
`extractMelody`) or from the chant grid — phrases of *notes*
instead of words — rendered offline like a chant and loaded
onto a pad. Chorus/bridge placement from VISION.md composes
on top of this: sections are just note-phrases with labels.

### Stage 4 — Typed words in your voice
Not in this plan, on purpose: it needs a neural model or a
phoneme-sliced calibration set (see VOICE-DESIGN.md). Stages
1–3 get built first; stage 4 gets revisited with evidence in
hand, not promised in advance.

### Build order
1. `Retune.kt` + a Retune button on the pad dialogs.
2. Calibration flow + sample storage.
3. "Your voice" instrument + note-phrase rendering.
Each stage ships as its own beta and gets Paul's ears on it
before the next begins.
