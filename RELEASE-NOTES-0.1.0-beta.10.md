# Woven Oak v0.1.0-beta.10 — Rough Beta

**Status: ROUGH BETA** — expect rough edges, and please report
them. Testers: phone model, Android version, what worked, what
broke, via GitHub Issues.

## New: the app tells you when there's an update

Paul's words for this one: *"every time that we publish a new
update that the app will get a message from us saying there's a
new update available do you wish to update?"*

So, from beta.10 on:

- When the app **launches**, it makes **one** small request to
  GitHub asking what the latest published Woven Oak release is,
  and compares it with the version you are running.
- If a newer one exists, a dialog says so — naming the new
  version and yours — with two buttons:
  - **Update** — opens the new version's APK in your browser;
    Android's own installer takes it from there. (The app never
    installs anything itself, silently or otherwise.)
  - **Later** — dismisses it, and it will not nag you again
    until the next time you open the app.
- If you are **offline**, the request just fails quietly. No
  error, no dialog, nothing blocked — the app opens as normal
  and the music never waits on a network.

There is **no** background polling, no service, no push
notification, no scheduled check. Launch is the only moment it
ever asks.

## The honest part: a second permission

Through beta.9, Woven Oak asked for exactly one permission —
the microphone. **Beta.10 asks for two: the microphone and
internet.** The internet permission exists solely for the
update check above. That one request is the app's **only**
network use, ever:

- It is a plain read of a public release listing — the same
  request a browser makes. Nothing about you, your voice bank,
  or your music is sent anywhere.
- No analytics. No telemetry. No other endpoints. No accounts.
- We deliberately did **not** add ACCESS_NETWORK_STATE: the
  check doesn't need it, and offline it simply fails silent.
- Everything else in the app — pads, chant, Retune, Calibrate,
  your voice bank — works **fully offline**, exactly as before.
  Airplane mode changes nothing but the update check.

## Unchanged

Calibrate, your playable voice, Retune, chant, the 30 built-in
sounds, hum-to-instrument, WAV gift export — all of beta.9 is
here, untouched. Your voice bank never leaves the phone; the
erase button ("Forget my voice") still deletes it whole.

## Checks

60 automated checks pass for this build: the whole voice suite
from beta.9 (the range judge, the voice bank, the pitch shifter
verified at every semitone) plus the notifier's own — version
comparison (newer / equal / older, beta ordering, a final
release outranking its betas), malformed release answers
treated as "no update" rather than a crash or a nag, and the
silent-offline path. One honest footnote: the fetch's success
path is exercised through the real fetch code over a stub
connection, because the build machine's sandbox routes JVM
loopback traffic into its overlay network and a live local
server test can't run there; the socket itself is stock
HttpURLConnection, the same call that ships.

## Releases

Per the project's release policy, only the two most recent
versions stay downloadable: beta.10 (current) and beta.9
(fallback). Beta.8 is retired with this release. Same signing
key throughout — beta.10 installs straight over the top,
including over beta.4 and everything since.
