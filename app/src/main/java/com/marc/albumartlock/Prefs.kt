package com.marc.albumartlock

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/** Small wrapper around the app's settings. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val filesDir: File = context.filesDir

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    // ---- Look. Defaults match Samsung's expanded lock screen player on a Galaxy S23. ----

    /** LAYOUT_CARD: rounded cover you can move and resize. LAYOUT_FILL: cover cropped to fill the screen. */
    var layout: Int
        get() = sp.getInt("layout", LAYOUT_CARD)
        set(v) = sp.edit().putInt("layout", v).apply()

    /** Background behind the card: BG_TINT, BG_BLUR or BG_BLACK. */
    var background: Int
        get() = sp.getInt("background", BG_TINT)
        set(v) = sp.edit().putInt("background", v).apply()

    /** Top edge of the cover, in percent of the screen height. */
    var coverTopPercent: Int
        get() = sp.getInt("cover_top", 29)
        set(v) = sp.edit().putInt("cover_top", v).apply()

    /** Cover width, in percent of the screen width. */
    var coverSizePercent: Int
        get() = sp.getInt("cover_size", 89)
        set(v) = sp.edit().putInt("cover_size", v).apply()

    /** Corner radius, in percent of the screen width (0 = square corners). */
    var cornerPercent: Int
        get() = sp.getInt("corner", 7)
        set(v) = sp.edit().putInt("corner", v).apply()

    var showTitle: Boolean
        get() = sp.getBoolean("show_title", true)
        set(v) = sp.edit().putBoolean("show_title", v).apply()

    var showArtist: Boolean
        get() = sp.getBoolean("show_artist", true)
        set(v) = sp.edit().putBoolean("show_artist", v).apply()

    var showAlbum: Boolean
        get() = sp.getBoolean("show_album", false)
        set(v) = sp.edit().putBoolean("show_album", v).apply()

    /** Where the text goes: TEXT_BELOW, TEXT_ABOVE or TEXT_ON_COVER. */
    var textPosition: Int
        get() = sp.getInt("text_position", TEXT_BELOW)
        set(v) = sp.edit().putInt("text_position", v).apply()

    /** Space between cover and text, in tenths of a percent of the screen height (30 = 3%). */
    var textGapTenths: Int
        get() = sp.getInt("text_gap", 30)
        set(v) = sp.edit().putInt("text_gap", v).apply()

    var textAlign: Int
        get() = sp.getInt("text_align", ALIGN_CENTER)
        set(v) = sp.edit().putInt("text_align", v).apply()

    /** Text size, in percent of the default. */
    var textScalePercent: Int
        get() = sp.getInt("text_scale", 100)
        set(v) = sp.edit().putInt("text_scale", v).apply()

    /** 0..80, percent of black laid over the background so the clock stays readable. */
    var dimPercent: Int
        get() = sp.getInt("dim_v2", 0)
        set(v) = sp.edit().putInt("dim_v2", v).apply()

    fun resetLook() {
        sp.edit().remove("layout").remove("background").remove("cover_top").remove("cover_size")
            .remove("corner").remove("show_title").remove("show_artist").remove("show_album")
            .remove("text_scale").remove("text_position").remove("text_gap").remove("text_align").remove("dim_v2").apply()
    }

    fun look(title: String, artist: String, album: String) = ArtRenderer.Look(
        layout = layout,
        background = background,
        coverTop = coverTopPercent / 100f,
        coverSize = coverSizePercent / 100f,
        corner = cornerPercent / 100f,
        title = if (showTitle) title else "",
        artist = if (showArtist) artist else "",
        album = if (showAlbum) album else "",
        textScale = textScalePercent / 100f,
        dimPercent = dimPercent,
        textPosition = textPosition,
        textGap = textGapTenths / 1000f,
        textAlign = textAlign,
    )

    // ---- Behaviour ----

    /** Look up an HD cover online (iTunes catalogue) when the music app only provides a small one. */
    var hdLookup: Boolean
        get() = sp.getBoolean("hd_lookup", true)
        set(v) = sp.edit().putBoolean("hd_lookup", v).apply()

    /** Seconds without playback before the normal lock screen comes back. */
    var restoreDelaySeconds: Int
        get() = sp.getInt("restore_delay", 30)
        set(v) = sp.edit().putInt("restore_delay", v).apply()

    /** Key of the track currently on the lock screen, or null when the normal wallpaper is showing. */
    var currentTrackKey: String?
        get() = sp.getString("current_track", null)
        set(v) = sp.edit().putString("current_track", v).apply()

    var lastStatus: String
        get() = sp.getString("last_status", "Nothing has happened yet.") ?: ""
        set(v) = sp.edit().putString("last_status", v).apply()

    /** Step-by-step log of the last cover search, shown in the app. */
    var lookupLog: String
        get() = sp.getString("lookup_log", "No song looked up yet.") ?: ""
        set(v) = sp.edit().putString("lookup_log", v).apply()

    /** Last song shown, so the settings preview can use the real cover. */
    var lastTitle: String
        get() = sp.getString("last_title", "Song title") ?: ""
        set(v) = sp.edit().putString("last_title", v).apply()
    var lastArtist: String
        get() = sp.getString("last_artist", "Artist") ?: ""
        set(v) = sp.edit().putString("last_artist", v).apply()
    var lastAlbum: String
        get() = sp.getString("last_album", "Album") ?: ""
        set(v) = sp.edit().putString("last_album", v).apply()
    val lastCoverFile: File get() = File(filesDir, "last_cover.png")

    /** The image the user picked as their normal lock screen. */
    val defaultWallpaperFile: File get() = File(filesDir, "default_lock_wallpaper")

    companion object {
        const val LAYOUT_CARD = 0
        const val LAYOUT_FILL = 1
        const val BG_TINT = 0
        const val BG_BLUR = 1
        const val BG_BLACK = 2
        const val TEXT_BELOW = 0
        const val TEXT_ABOVE = 1
        const val TEXT_ON_COVER = 2
        const val ALIGN_CENTER = 0
        const val ALIGN_LEFT = 1
    }
}
