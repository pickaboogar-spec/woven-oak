# Woven Oak — Voice Training & Your-Voice Instrument
## Design brief (Paul, 2026-11-07) — "as good as Auto-Tune, but better"

Paul's ask, in his words: the app tells you what to sing, plays
you a range to sing it at, listens to your range and the words
you sing, and learns to recreate your voice and your range —
then creates new music from what it has learned is your voice.
His benchmark: Auto-Tune proves pitch work is possible in an
app; ours should go further — Auto-Tune only *corrects* a voice,
ours should *learn* one.

### What Auto-Tune actually does (the part that's proven)
Real-time pitch detection, then the sung pitch is pulled to the
nearest note of a chosen scale ("retune"). Two ingredients:
accurate pitch tracking, and pitch shifting that doesn't wreck
the voice's character. Woven Oak already has ingredient one —
the pitch readout names notes and tracks vocal range live.

### The honest map of Paul's feature, in stages

**Stage 1 — Retune (Auto-Tune's home turf).** After a take is
recorded, detect the pitch contour frame by frame, snap each
frame to the song's scale, resynthesize. Feasible on-device
with the tracker we have plus time-domain pitch shifting
(PSOLA-style). This ships first and proves the pipeline.

**Stage 2 — The calibration song (Paul's design).** The app
plays a short original lyric line by line, each line at a
guided pitch, walking the singer down and up through their
range. From those takes it records: lowest/highest comfortable
notes (the range map), and clean sustained sounds at many
pitches (the raw material of stage 3). This is the piece
Auto-Tune doesn't have — it never asks who you are.

**Stage 3 — Your voice as an instrument.** The calibration
takes are sliced into note-samples across the range. After
that, melodies can be *played* in the user's own voice —
pick notes, and their recorded voice sings them. This is the
concatenative approach (the same family of technique the
classic vocal synthesizers use), and it is honestly buildable
on-device because the samples are the user's own.

**Stage 4 — Typed words, sung in your voice (the frontier).**
Words the user never sang, sung back in their voice. Needs
either far more calibration material (the calibration lyric
designed to cover the sounds of the language, then sliced and
reassembled — possible, grainy, charming) or a neural voice
model (not honestly buildable inside an offline, mic-only
phone app today; would need a server and training data, which
breaks Woven Oak's constitution). Recorded here as the
horizon, not a promise — stages 1–3 are the buildable road.

### Guardrails
- The voiceprint is the user's own voice, recorded by them, on
  their device, and — like everything in Woven Oak — it never
  leaves the phone. No cloning anyone else's voice: the
  calibration only works sung live into the mic.
- Pitch correction strength is a dial, not a mandate — the
  natural take always stays available.
