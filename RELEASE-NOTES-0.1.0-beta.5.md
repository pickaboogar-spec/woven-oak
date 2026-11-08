# Woven Oak v0.1.0-beta.5 — Rough Beta

**Status: ROUGH BETA** — expect rough edges, and please report
them. Testers: phone model, Android version, what worked, what
broke, via GitHub Issues.

Two fixes, both from Paul's first hands-on with beta.4:

## The pads are just numbers now

The pads used to carry two or three lines of small state text
under the number, and on a real phone the bottom line got cut
off. Now each pad shows one big number — **1 through 6** — and
nothing else. You can still read the board at a glance: red
means recording, green means looping, the pad breathes while it
plays, and the play-together row shows which pads are sounding.

## Chant mode actually answers back now

First field report: "Build & play" seemed to do nothing. So:

- The chant render is half as long (16 beats), the size of an
  ordinary pad take — the size phones loop without complaint.
- Building a chant **stops the six pads first**, so what you
  hear is the chant itself, standing alone.
- If fewer than two pads hold phrases, the status line now
  tells you exactly how many it found instead of leaving you
  guessing.

To hear it: record short phrases on at least two pads, tap
**🤖 Chant…**, pick a style, **Build & play**.

## Also: beta.1 is retired

Beta.1's release, file and tag were removed from this repo at
Paul's direction. Its signing key was a one-off that no longer
exists, so it could never update cleanly. Beta.2 onward all
share one kept key — this beta.5 installs straight over beta.2,
beta.3 or beta.4.
