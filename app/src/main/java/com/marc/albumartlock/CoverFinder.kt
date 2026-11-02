package com.marc.albumartlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Finds the best cover for a song and writes down every step, so the app can show exactly
 * which sources were tried and why each one did or didn't work.
 *
 * Order: HD from iTunes, then HD from Deezer (each needs an exact title + artist match, unless the
 * search returned exactly one result, which is then used as is), then the
 * music app's own cover (a full-size version of its link if it gives one, else the image it shared).
 */
class CoverFinder(private val context: Context, private val hdLookup: Boolean) {

    val log = mutableListOf<String>()

    data class Song(
        val app: String,
        val title: String,
        val artist: String,
        val album: String,
        val bitmap: Bitmap?,
        val uri: String?,
    )

    fun find(song: Song): Pair<Bitmap?, String> {
        log += "App: ${song.app}"
        log += "Song: \"${song.title}\" by \"${song.artist}\"" +
            (if (song.album.isNotBlank()) ", album \"${song.album}\"" else "")
        log += "Cover shared by the app: " +
            (song.bitmap?.let { "${it.width}×${it.height} image" } ?: "no image") +
            (if (song.uri.isNullOrBlank()) ", no link" else ", link ${song.uri.take(90)}")

        if (hdLookup) {
            tryHd("iTunes") { itunes(song) }?.let { return it to "HD from iTunes" }
            tryHd("Deezer") { deezer(song) }?.let { return it to "HD from Deezer" }
        } else {
            log += "HD lookup is turned off."
        }

        val candidates = mutableListOf<Pair<Bitmap, String>>()
        song.bitmap?.let { candidates += it to "shared by the music app" }
        val uri = song.uri
        if (!uri.isNullOrBlank()) {
            val big = upscaleUrl(uri)
            if (big != uri) {
                log += "Music app link, full size: trying…"
                download(big)?.let {
                    log += "  got ${it.width}×${it.height}"
                    candidates += it to "full size from the music app"
                }
            }
            if (candidates.none { it.second == "full size from the music app" }) {
                download(uri)?.let {
                    log += "Music app link: got ${it.width}×${it.height}"
                    candidates += it to "linked by the music app"
                }
            }
        }
        val best = candidates.maxByOrNull { minOf(it.first.width, it.first.height) }
        log += if (best != null) "Using: ${best.second} (${best.first.width}×${best.first.height})"
            else "No cover found anywhere."
        return best?.first to (best?.second ?: "")
    }

    private fun tryHd(name: String, block: () -> Bitmap?): Bitmap? {
        log += "$name: searching…"
        return try {
            block()?.also { log += "  $name: got ${it.width}×${it.height} ✓" }
        } catch (e: Exception) {
            log += "  $name failed: ${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }

    // ---- iTunes ------------------------------------------------------------------------------

    private fun itunes(song: Song): Bitmap? {
        val q = query(song) ?: return null
        val json = getText("https://itunes.apple.com/search?term=${enc(q)}&entity=song&limit=25") ?: return null
        val results = JSONObject(json).getJSONArray("results")
        log += "  ${results.length()} results"
        var pick: JSONObject? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (!matches(song, r.optString("artistName"), r.optString("trackName"))) continue
            if (pick == null || sameAlbum(song, r.optString("collectionName"))) pick = r
        }
        if (pick == null && results.length() == 1) {
            pick = results.getJSONObject(0)
            log += "  only one result, using it even though the " +
                mismatch(song, pick.optString("artistName"), pick.optString("trackName"))
        }
        if (pick == null) {
            logNoMatch(song, (0 until results.length()).map { results.getJSONObject(it) }
                .map { it.optString("artistName") to it.optString("trackName") })
            return null
        }
        log += "  match: \"${pick.optString("trackName")}\" by ${pick.optString("artistName")}"
        val art = pick.optString("artworkUrl100").takeIf { it.isNotEmpty() } ?: return null
        return download(art.replace(Regex("\\d+x\\d+bb"), "${HD}x${HD}bb"))
    }

    // ---- Deezer ------------------------------------------------------------------------------

    private fun deezer(song: Song): Bitmap? {
        val q = query(song) ?: return null
        val json = getText("https://api.deezer.com/search?q=${enc(q)}&limit=25") ?: return null
        val results = JSONObject(json).optJSONArray("data") ?: return null
        log += "  ${results.length()} results"
        var pick: JSONObject? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val artist = r.optJSONObject("artist")?.optString("name") ?: ""
            if (!matches(song, artist, r.optString("title"))) continue
            val album = r.optJSONObject("album")?.optString("title") ?: ""
            if (pick == null || sameAlbum(song, album)) pick = r
        }
        if (pick == null && results.length() == 1) {
            pick = results.getJSONObject(0)
            log += "  only one result, using it even though the " +
                mismatch(song, pick.optJSONObject("artist")?.optString("name") ?: "", pick.optString("title"))
        }
        if (pick == null) {
            logNoMatch(song, (0 until results.length()).map { results.getJSONObject(it) }
                .map { (it.optJSONObject("artist")?.optString("name") ?: "") to it.optString("title") })
            return null
        }
        log += "  match: \"${pick.optString("title")}\""
        val xl = pick.optJSONObject("album")?.optString("cover_xl")?.takeIf { it.isNotEmpty() } ?: return null
        // cover_xl is 1000 px; Deezer's image server can usually make bigger ones.
        return download(xl.replace("1000x1000", "${HD}x$HD")) ?: download(xl)
    }

    // ---- Matching ----------------------------------------------------------------------------

    private fun clean(t: String) = t.replace(Regex("\\s*[(\\[].*?[)\\]]"), "") // "(feat. …)", "[Remix]"
        .replace(Regex("\\s+-\\s+.*$"), "") // "Song - Remastered 2011"
        .trim()

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    /** "SoFaygo & slayr" -> [SoFaygo, slayr]. Catalogues often list a different one as the main artist. */
    private fun artists(a: String) =
        a.split(",", "&", " x ", " X ", " and ", " feat.", " feat ", " Feat.", " ft.", " with ", " vs. ")
            .map { it.trim() }.filter { it.isNotEmpty() }

    private fun mainArtist(a: String) = artists(a).firstOrNull() ?: a.trim()

    private fun query(song: Song): String? {
        if (song.title.isBlank()) { log += "  no title to search for"; return null }
        return "${mainArtist(song.artist)} ${clean(song.title)}".trim()
    }

    /** Only accept a confident match, so we never show the wrong cover. */
    private fun matches(song: Song, artist: String, title: String): Boolean = mismatch(song, artist, title) == null

    /** Why a result isn't a match, or null when it is. Any of the song's artists may be the catalogue's main artist. */
    private fun mismatch(song: Song, artist: String, title: String): String? {
        val wanted = artists(song.artist).map { norm(it) }.filter { it.isNotEmpty() }
        val a = norm(artist)
        val artistOk = wanted.isEmpty() || wanted.any { w -> a.contains(w) || (a.isNotEmpty() && w.contains(a)) }
        val titleOk = norm(clean(title)) == norm(clean(song.title))
        return when {
            !artistOk && !titleOk -> "artist and title differ"
            !artistOk -> "artist differs"
            !titleOk -> "title differs"
            else -> null
        }
    }

    private fun sameAlbum(song: Song, album: String) =
        song.album.isNotBlank() && norm(clean(album)) == norm(clean(song.album))

    private fun logNoMatch(song: Song, results: List<Pair<String, String>>) {
        log += "  no exact match"
        for ((artist, title) in results.take(3)) {
            log += "    \"$title\" by $artist: ${mismatch(song, artist, title)}"
        }
    }

    // ---- Network -----------------------------------------------------------------------------

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "AlbumArtLockScreen/1.0 (Android)")
        }

    private fun getText(url: String): String? {
        val conn = open(url)
        val code = conn.responseCode
        if (code != 200) {
            log += "  HTTP $code from ${URL(url).host}"
            return null
        }
        return conn.inputStream.use { it.bufferedReader().readText() }
    }

    fun download(uri: String): Bitmap? = try {
        if (uri.startsWith("http")) {
            val conn = open(uri)
            val code = conn.responseCode
            if (code != 200) {
                log += "  HTTP $code for image from ${URL(uri).host}"
                null
            } else {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    ?: null.also { log += "  image from ${URL(uri).host} couldn't be decoded" }
            }
        } else {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (e: Exception) {
        log += "  image download failed: ${e.javaClass.simpleName}: ${e.message}"
        null
    }

    /** Rewrites known cover URLs (Google/YouTube image servers) to ask for a large size. */
    private fun upscaleUrl(url: String): String {
        if (!url.startsWith("http")) return url
        var u = url
        if ("googleusercontent.com" in u || "ggpht.com" in u) {
            u = u.replace(Regex("=w\\d+-h\\d+"), "=w$HD-h$HD").replace(Regex("=s\\d+"), "=s$HD")
        }
        if ("ytimg.com" in u) {
            u = u.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/maxresdefault.jpg")
        }
        return u
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val HD = 1200
    }
}
