/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 *
 * The update notifier (beta.10, at Paul's direction): "every time
 * that we publish a new update that the app will get a message
 * from us saying there's a new update available do you wish to
 * update?"
 *
 * Design, kept deliberately small:
 * - Runs ONCE per launch, from MainActivity.onCreate. No
 *   background polling, no services, no push, no scheduled work.
 * - ONE small GET to GitHub's public latest-release endpoint for
 *   this repo. That request is the app's ONLY network use, ever:
 *   no analytics, no telemetry, no other endpoints, and nothing
 *   about the user or their music is sent — it is a plain read of
 *   a public file, the same request a browser would make.
 * - Offline, timed out, or garbled answer: fail SILENT. The app
 *   just opens as normal. Music never waits on a network.
 * - "Update" hands the release's APK download URL to the user's
 *   browser; Android's own installer takes it from there. The
 *   app never installs anything itself.
 *
 * The pure logic (parse / compare / extract) is separated from
 * the one network call so tests/run-checks.sh can exercise it on
 * the JVM — including the offline path, against an address where
 * nothing answers.
 */
package com.damonini.wovenoak

import java.net.HttpURLConnection
import java.net.URL

object UpdateCheck {

    /** The only URL this app ever contacts. */
    const val LATEST_URL =
        "https://api.github.com/repos/pickaboogar-spec/woven-oak/releases/latest"

    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private const val MAX_BODY_CHARS = 512 * 1024

    /** A published release, as far as the notifier cares. */
    data class ReleaseInfo(
        /** Raw tag, e.g. "v0.1.0-beta.10". */
        val tag: String,
        /** Tag without the leading "v": "0.1.0-beta.10". */
        val versionName: String,
        /** Direct download URL of the release's .apk asset. */
        val apkUrl: String
    )

    /**
     * Parse a version name into a comparable key:
     * "v0.1.0-beta.10" → [0, 1, 0, 10]; a plain release like
     * "0.2.0" → [0, 2, 0, ∞] so a final release outranks its own
     * betas. Returns null for anything malformed — and malformed
     * is always treated as "not newer", never as a reason to nag.
     */
    fun parseVersion(raw: String): List<Int>? {
        var s = raw.trim()
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1)
        if (s.isEmpty()) return null
        val dash = s.indexOf('-')
        val core = if (dash >= 0) s.substring(0, dash) else s
        val suffix = if (dash >= 0) s.substring(dash + 1) else ""
        val parts = core.split('.')
        if (parts.isEmpty() || parts.size > 4) return null
        val nums = ArrayList<Int>()
        for (p in parts) {
            if (p.isEmpty() || !p.all { it.isDigit() }) return null
            nums.add(p.toIntOrNull() ?: return null)
        }
        while (nums.size < 3) nums.add(0)
        val stage = when {
            suffix.isEmpty() -> Int.MAX_VALUE
            suffix.startsWith("beta.") ->
                suffix.substring(5).toIntOrNull() ?: return null
            suffix.startsWith("beta") ->
                suffix.substring(4).toIntOrNull() ?: return null
            else -> return null
        }
        nums.add(stage)
        return nums
    }

    /** Negative / zero / positive like compareTo; null if either is malformed. */
    fun compareVersions(a: String, b: String): Int? {
        val ka = parseVersion(a) ?: return null
        val kb = parseVersion(b) ?: return null
        for (i in 0 until maxOf(ka.size, kb.size)) {
            val x = ka.getOrElse(i) { 0 }
            val y = kb.getOrElse(i) { 0 }
            if (x != y) return if (x < y) -1 else 1
        }
        return 0
    }

    /** True only when [remoteTag] is a well-formed version strictly newer than [ownVersion]. */
    fun isNewer(remoteTag: String, ownVersion: String): Boolean {
        val c = compareVersions(remoteTag, ownVersion) ?: return false
        return c > 0
    }

    /** The value of a "key": "value" string field, or null. Tolerates escapes. */
    private fun jsonStringAfter(json: String, keyAt: Int): String? {
        val colon = json.indexOf(':', keyAt)
        if (colon < 0) return null
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return null
        val sb = StringBuilder()
        i++
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                sb.append(json[i + 1]); i += 2; continue
            }
            if (c == '"') return sb.toString()
            sb.append(c); i++
        }
        return null
    }

    /**
     * Pull the tag and the .apk download URL out of a GitHub
     * latest-release JSON body. Hand-rolled because the app ships
     * with no JSON library (framework + stdlib only, on purpose).
     * Returns null on anything unexpected: missing tag, no .apk
     * asset, empty or garbled body.
     */
    fun parseLatestRelease(json: String): ReleaseInfo? {
        if (json.isBlank()) return null
        val tagKey = json.indexOf("\"tag_name\"")
        if (tagKey < 0) return null
        val tag = jsonStringAfter(json, tagKey)?.trim().orEmpty()
        if (tag.isEmpty() || parseVersion(tag) == null) return null
        var apkUrl: String? = null
        var from = 0
        while (true) {
            val k = json.indexOf("\"browser_download_url\"", from)
            if (k < 0) break
            val url = jsonStringAfter(json, k)
            if (url != null && url.endsWith(".apk", ignoreCase = true)) {
                apkUrl = url; break
            }
            from = k + 1
        }
        val apk = apkUrl ?: return null
        return ReleaseInfo(tag, tag.removePrefix("v").removePrefix("V"), apk)
    }

    /**
     * The one network call. Returns null on ANY failure —
     * offline, timeout, non-200, unreadable body — and never
     * throws. [urlText] and [opener] are parameters only so the
     * check suite can point the fetch at dead addresses and at a
     * stub connection; the app itself always uses the defaults.
     */
    fun fetchLatest(
        urlText: String = LATEST_URL,
        opener: (String) -> HttpURLConnection = {
            URL(it).openConnection() as HttpURLConnection
        }
    ): ReleaseInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = opener(urlText).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "WovenOak-UpdateCheck")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.length > MAX_BODY_CHARS) return null
            parseLatestRelease(body)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
