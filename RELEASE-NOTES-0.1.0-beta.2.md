# Woven Oak v0.1.0-beta.2 — Rough Beta

**Status: ROUGH BETA** — same warning as beta.1: expect rough
edges, and please report them.

Built the same evening as beta.1, from the first real phone test
(Paul, Moto G). Two fixes, both his:

1. **The pads say how to clear them.** A permanent line under the
   title now reads "Tap a pad to record · Hold a pad to clear it."
   In beta.1 the hold-to-clear gesture existed but was only
   whispered in small print on a filled pad — the first tester had
   to find it by accident.
2. **Play together row.** Six big numbered toggles under Play all /
   Stop all. Tap a number to add that pad to what's playing, tap
   it again to drop it out — play pads 1 + 4, add 3, drop 4, hold
   a pad to clear it and re-record, all live.

## Install note

Beta.2 is signed with a new key (the beta.1 build machine's debug
key did not survive), so Android will not install it over beta.1.
**Uninstall beta.1 first, then install beta.2.** Nothing is lost:
loops live in memory, and the sound library is built in. From
beta.2 onward the signing key is kept with the project, so future
updates will install straight over the top.

## Testers wanted

Phone model, Android version, what worked, what broke — GitHub
Issues on this repo. That loop is how beta.1 became beta.2 in one
evening.
