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

    /** STYLE_FIT_BLUR: sharp cover centred over a blurred copy. STYLE_FILL: cover cropped to fill. */
    var style: Int
        get() = sp.getInt("style", STYLE_FIT_BLUR)
        set(v) = sp.edit().putInt("style", v).apply()

    /** 0..80, percent of black laid over the image so the clock stays readable. */
    var dimPercent: Int
        get() = sp.getInt("dim", 25)
        set(v) = sp.edit().putInt("dim", v).apply()

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

    /** The image the user picked as their normal lock screen. */
    val defaultWallpaperFile: File get() = File(filesDir, "default_lock_wallpaper")

    companion object {
        const val STYLE_FIT_BLUR = 0
        const val STYLE_FILL = 1
    }
}
