# Woven Oak v0.1.0-beta.9 — Rough Beta

**Status: ROUGH BETA** — expect rough edges, and please report
them. Testers: phone model, Android version, what worked, what
broke, via GitHub Issues.

## New: Calibrate — teach the app your voice

Stage 2 and the first half of stage 3 of the voice plan
(VOICE-PLAN.md) are here. The app learns your voice the way
Auto-Tune never asks to: by listening to who you are.

Tap **🎙 Calibrate…** and the app sings first:

1. Sing any note that feels comfortable — that becomes home base.
2. The app plays a guide note one step lower; you sing it back
   on a steady "ahh" (an original calibration lyric gives you a
   line per note — sing the words or just the sound). A note is
   only banked when you really sang it: on pitch, and held
   steady. Miss twice and the walk takes that as the edge of
   your range — no failing, just mapping.
3. Then the walk climbs, the same way, to the top.

Every accepted note keeps its steadiest second in a **voice
bank** that lives in the app's private storage on your phone.

## New: your voice, playable

- **Sounds…** now starts with **"Your voice — a phrase in your
  own voice"**: the familiar rising phrase, sung by you.
- **Hum → instrument…** gains **"Your voice"**: hum a melody,
  and your banked voice sings it back on the pad — each note
  taken from your nearest banked sample and shifted home with a
  WSOLA pitch shifter, entirely on-device.

## What it does NOT do

- It does **not** sing words you type in your voice. That is
  stage 4 of the plan — the frontier — and it is not in this
  app. Your voice here plays *notes*: phrases and melodies.
- The range judge wants a steady "ahh". Heavy vibrato reads as
  unsteady and the note won't bank — sing it plain.
- Notes far outside your banked range clamp to the nearest
  honest octave rather than stretching your voice into a chipmunk.

## Privacy, as always

The voice bank never leaves the phone. The app's only
permission is still the microphone. There is an erase button
inside Calibrate ("Forget my voice") that deletes the whole
bank. No accounts, no telemetry, no uploads — free forever.

## Checks

33 automated checks pass for this build (the range judge's
accept/reject rules, the voice bank's storage round-trip, the
pitch shifter verified at every semitone step from −12 to +12
by two independent measurements, hum-to-your-voice conversion,
and phrase rendering). The suite lives in `tests/` if you want
to run it yourself.

## Releases

Per the project's release policy, only the two most recent
versions stay downloadable: beta.9 (current) and beta.8
(fallback). Beta.7 is retired with this release. Same signing
key throughout — beta.9 installs straight over the top.
