# Woven Oak — Release Policy (Paul, 2026-11-07)

**Keep the two most recent versions downloadable — nothing
older.** The current release, plus the previous one as the
tested fallback: if the current version doesn't work, the old
version does, because we've already tested it.

Mechanics, applied on every new release:

- Delete the release page of anything older than the previous
  version (GitHub web UI).
- Remove its APK from the repo tree; delete its tag once the
  release is gone.
- All releases sign with the one kept key
  (`~/workspace/woven-oak-signing/debug.keystore` on Pepper's
  machine), so every version installs over the previous one.

History: beta.1 was retired outright on 2026-11-07 (release,
file, tag) because its one-off signing key no longer exists.
Beta.2 and beta.3 were retired the same night under this
policy, leaving beta.4 (fallback) and beta.5 (current).
