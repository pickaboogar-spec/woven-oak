# The Chant Template — design note (Paul, 2026-11-07)

**Status: SHIPPED in v0.1.0-beta.3, the same evening.** What
follows is the design as drawn up before the build.

Paul's idea, in his framing: imitate **the way** a certain famous
robot-chant record works — never its music, never its words. What
that record proved is a *mechanism*: a driving electronic pulse, a
processed voice chanting short command phrases one to a beat, the
phrases stacking and cycling until the list itself becomes the
song. The mechanism is a style, and styles can be imitated. The
template ships **empty** — every word in a finished chant is the
user's own.

## How a user pours words in

1. **Anchor word** — one word of the user's own, repeated between
   verses as the refrain. Their word, their song.
2. **Slots** — 8 to 16 short phrases, one per slot. The shape that
   lands: a verb plus one short word, two or three syllables
   ("chop the onions", "stir the pot", "ship the code"). The
   user's own world supplies the list — kitchen, garden, workshop,
   boat, desk.
3. **Voice** — the user speaks each phrase into a slot (the pads
   already record exactly like this). The engine trims each take
   to the beat.
4. **Build** — the chant sequences over a steady pulse at a chosen
   tempo: first pass runs half the slots, second pass runs them
   all, the anchor word lands every four beats. An optional
   robot-voice treatment (pitch/formant shift) is the user's
   choice, off by default.

## Rules baked in

- User's own words only. No preset lyric sheet, no phrase list
  borrowed from any existing song.
- The anchor slot takes the user's word — the template never
  suggests one from somebody else's record.
- Everything on-device, like the rest of Woven Oak. One
  permission: the microphone.

## Where it lives

A future **Chant mode** in Woven Oak, after the beta.2 field
fixes: pads become phrase slots, the Play-together number row
becomes the step sequencer, export stays a giftable WAV.
