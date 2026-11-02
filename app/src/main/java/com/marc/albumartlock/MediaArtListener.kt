package com.marc.albumartlock

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * Runs while notification access is granted. Android only lets apps with that access see
 * other apps' media sessions, which is where the playing song and its cover come from.
 * This service never reads or touches the notifications themselves.
 */
class MediaArtListener : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private var sessionManager: MediaSessionManager? = null
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var restorePending = false
    /** Track key we are fetching/applying right now, so we don't do the same work twice. */
    private var inFlightKey: String? = null
    /** Set when the look changed, so the current song is drawn again even though it didn't change. */
    private var redrawNext = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers ?: emptyList())
    }

    private val restoreRunnable = Runnable {
        restorePending = false
        restoreNormalWallpaper()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        prefs = Prefs(this)
        // Redraw the playing song on (re)connect, e.g. after an app update changed the look.
        redrawNext = true
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager = msm
        val me = ComponentName(this, MediaArtListener::class.java)
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, me, main)
            watch(msm.getActiveSessions(me))
        } catch (e: SecurityException) {
            status("Notification access is missing: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        unwatchAll()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        instance = null
        main.removeCallbacksAndMessages(null)
        unwatchAll()
        worker.shutdown()
        super.onDestroy()
    }

    private fun watch(controllers: List<MediaController>) {
        unwatchAll()
        for (c in controllers) {
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = evaluate()
                override fun onMetadataChanged(metadata: MediaMetadata?) = evaluate()
                override fun onSessionDestroyed() = evaluate()
            }
            c.registerCallback(cb, main)
            callbacks[c] = cb
        }
        evaluate()
    }

    private fun unwatchAll() {
        for ((c, cb) in callbacks) runCatching { c.unregisterCallback(cb) }
        callbacks.clear()
    }

    /** Called from the settings screen after a change. */
    fun refresh(forceRedraw: Boolean) {
        main.post {
            if (forceRedraw) redrawNext = true
            evaluate()
        }
    }

    private fun isPlaying(c: MediaController): Boolean {
        val s = c.playbackState?.state ?: return false
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
    }

    private fun evaluate() {
        if (!::prefs.isInitialized) return
        val playing = callbacks.keys.firstOrNull { isPlaying(it) }

        if (!prefs.enabled) {
            // Switched off (app or quick settings tile): put the normal lock screen back now.
            main.removeCallbacks(restoreRunnable)
            restorePending = false
            restoreNormalWallpaper()
            return
        }

        if (playing == null) {
            // Nothing playing: put the normal lock screen back after a grace period,
            // so a short pause or skip doesn't make the wallpaper flash.
            if (prefs.currentTrackKey != null && !restorePending) {
                restorePending = true
                main.postDelayed(restoreRunnable, prefs.restoreDelaySeconds * 1000L)
            }
            return
        }

        if (restorePending) {
            main.removeCallbacks(restoreRunnable)
            restorePending = false
        }

        val md = playing.metadata ?: return
        val key = trackKey(playing.packageName, md)
        if ((key == prefs.currentTrackKey && !redrawNext) || key == inFlightKey) return

        val bitmap = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        // Players often send the title first and the cover a moment later; wait for it.
        if (bitmap == null && uri.isNullOrBlank()) return

        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown track"
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val look = prefs.look(title, artist, album)
        inFlightKey = key
        redrawNext = false
        worker.execute {
            val (art, source) = bestCover(bitmap, uri, title, artist, album)
            if (art == null) {
                main.post { if (inFlightKey == key) inFlightKey = null }
                status("Couldn't get the cover for \"$title\".")
                return@execute
            }
            val ok = applyCover(art, look)
            saveForPreview(art, title, artist, album)
            main.post {
                if (inFlightKey == key) inFlightKey = null
                if (ok) prefs.currentTrackKey = key
            }
            if (ok) status("Showing the cover of \"$title\" (${art.width}×${art.height}, $source).")
        }
    }

    private fun trackKey(pkg: String, md: MediaMetadata): String = listOf(
        pkg,
        md.getString(MediaMetadata.METADATA_KEY_TITLE),
        md.getString(MediaMetadata.METADATA_KEY_ARTIST),
        md.getString(MediaMetadata.METADATA_KEY_ALBUM),
    ).joinToString("|")

    /**
     * Music apps usually hand over a small cover (often 300-500 px), which looks soft on a
     * 1080 px wide screen. So the HD cover from the iTunes catalogue comes first (if allowed and
     * the song matches exactly). Only when there is none do we fall back to the music app's own
     * cover: a full-size version of its cover URL if possible, otherwise the bitmap it shared.
     * Returns the cover with a short description of where it came from.
     */
    private fun bestCover(bitmap: Bitmap?, uri: String?, title: String, artist: String, album: String): Pair<Bitmap?, String> {
        if (prefs.hdLookup) {
            itunesCover(title, artist, album)?.let { return it to "HD from iTunes" }
        }
        val candidates = mutableListOf<Pair<Bitmap, String>>()
        bitmap?.let { candidates += it to "from the music app (no HD match)" }
        if (!uri.isNullOrBlank()) {
            val big = upscaleUrl(uri)
            val loaded = (if (big != uri) loadUri(big) else null)?.let { it to "full size from the music app (no HD match)" }
                ?: loadUri(uri)?.let { it to "from the music app (no HD match)" }
            loaded?.let { candidates += it }
        }
        val best = candidates.maxByOrNull { minOf(it.first.width, it.first.height) }
        return (best?.first) to (best?.second ?: "")
    }

    /** Rewrites known cover URLs (Google/YouTube image servers) to ask for a large size. */
    private fun upscaleUrl(url: String): String {
        if (!url.startsWith("http")) return url
        var u = url
        if ("googleusercontent.com" in u || "ggpht.com" in u) {
            u = u.replace(Regex("=w\\d+-h\\d+"), "=w$HD_SIZE-h$HD_SIZE")
                .replace(Regex("=s\\d+"), "=s$HD_SIZE")
        }
        if ("ytimg.com" in u) {
            u = u.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/maxresdefault.jpg")
        }
        return u
    }

    /** Looks the song up in Apple's public iTunes Search API, which serves covers up to 3000 px. */
    private fun itunesCover(title: String, artist: String, album: String): Bitmap? = runCatching {
        if (title.isBlank()) return null
        fun clean(t: String) = t.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim() // drop "(feat. …)", "[Remix]"
        val cleanTitle = clean(title)
        val mainArtist = artist.split(",", "&", " x ", " feat", " ft.").first().trim()
        val term = java.net.URLEncoder.encode("$mainArtist $cleanTitle", "UTF-8")
        val conn = URL("https://itunes.apple.com/search?term=$term&entity=song&limit=10").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val json = conn.inputStream.use { it.bufferedReader().readText() }
        val results = org.json.JSONObject(json).getJSONArray("results")
        fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
        val wantArtist = norm(mainArtist)
        val wantTitle = norm(cleanTitle)
        val wantAlbum = norm(album)
        var pick: org.json.JSONObject? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val a = norm(r.optString("artistName"))
            val t = norm(clean(r.optString("trackName")))
            // Only accept a confident match, so we never show the wrong cover.
            if (wantArtist.isEmpty() || !(a.contains(wantArtist) || wantArtist.contains(a))) continue
            if (t != wantTitle) continue
            if (pick == null || (wantAlbum.isNotEmpty() && norm(r.optString("collectionName")) == wantAlbum)) pick = r
        }
        val art = pick?.optString("artworkUrl100")?.takeIf { it.isNotEmpty() } ?: return null
        loadUri(art.replace(Regex("\\d+x\\d+bb"), "${HD_SIZE}x${HD_SIZE}bb"))
    }.getOrNull()

    private fun loadUri(uri: String): Bitmap? = runCatching {
        if (uri.startsWith("http")) {
            val conn = URL(uri).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } else {
            contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    private fun screenSize(): Pair<Int, Int> {
        val dm = getSystemService(DisplayManager::class.java)!!
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val w = minOf(metrics.widthPixels, metrics.heightPixels)
        val h = maxOf(metrics.widthPixels, metrics.heightPixels)
        return w to h
    }

    private fun applyCover(art: Bitmap, look: ArtRenderer.Look): Boolean = try {
        val (w, h) = screenSize()
        val image = ArtRenderer.render(art, w, h, look)
        WallpaperManager.getInstance(this).setBitmap(image, null, true, WallpaperManager.FLAG_LOCK)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Setting the lock screen failed", e)
        status("Setting the lock screen failed: ${e.message}")
        false
    }

    /** Keeps the last cover so the settings screen can preview the look with a real song. */
    private fun saveForPreview(art: Bitmap, title: String, artist: String, album: String) {
        runCatching {
            prefs.lastCoverFile.outputStream().use { art.compress(Bitmap.CompressFormat.PNG, 100, it) }
            prefs.lastTitle = title
            prefs.lastArtist = artist
            prefs.lastAlbum = album
        }
    }

    private fun restoreNormalWallpaper() {
        if (prefs.currentTrackKey == null) return
        prefs.currentTrackKey = null
        val file = prefs.defaultWallpaperFile
        worker.execute {
            try {
                val wm = WallpaperManager.getInstance(this)
                if (file.exists()) {
                    FileInputStream(file).use { wm.setStream(it, null, true, WallpaperManager.FLAG_LOCK) }
                    status("Music stopped, so your own lock screen image is back.")
                } else {
                    // Without a saved image, the lock screen falls back to the home screen wallpaper.
                    wm.clear(WallpaperManager.FLAG_LOCK)
                    status("Music stopped, so the lock screen now uses your home screen wallpaper.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restoring the lock screen failed", e)
                status("Restoring the lock screen failed: ${e.message}")
            }
        }
    }

    private fun status(text: String) {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        Prefs(this).lastStatus = "$time: $text"
    }

    companion object {
        private const val TAG = "AlbumArtLock"
        private const val HD_SIZE = 1200
        @Volatile var instance: MediaArtListener? = null
    }
}
