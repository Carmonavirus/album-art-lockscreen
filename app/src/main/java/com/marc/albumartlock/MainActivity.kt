package com.marc.albumartlock

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** One simple settings screen, built in code so there are no extra libraries to depend on. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var accessStatus: TextView
    private lateinit var accessButton: Button
    private lateinit var lastStatus: TextView
    private lateinit var defaultStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        setContentView(ScrollView(this).apply { addView(root, MATCH_PARENT, WRAP_CONTENT) })

        root.addView(text("Album Art Lock Screen", 24f, bold = true))
        root.addView(text("While music plays, the song's cover becomes your lock screen. " +
            "Your normal lock screen comes back when the music stops.", 15f, top = 8))

        // 1. Access
        root.addView(header("1. Allow access"))
        accessStatus = text("", 15f)
        root.addView(accessStatus)
        accessButton = Button(this).apply {
            text = "Open notification access settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        root.addView(accessButton)

        // 2. On/off
        root.addView(header("2. Turn it on"))
        root.addView(Switch(this).apply {
            text = "Show album art on the lock screen"
            textSize = 16f
            isChecked = prefs.enabled
            setOnCheckedChangeListener { _, on ->
                prefs.enabled = on
                MediaArtListener.instance?.refresh(forceRedraw = false)
            }
        })

        // 3. Look
        root.addView(header("3. Look"))
        val styles = RadioGroup(this)
        val fit = RadioButton(this).apply { id = 1001; text = "Cover in the middle, blurred cover behind it" }
        val fill = RadioButton(this).apply { id = 1002; text = "Cover fills the whole screen (cropped)" }
        styles.addView(fit); styles.addView(fill)
        styles.check(if (prefs.style == Prefs.STYLE_FILL) 1002 else 1001)
        styles.setOnCheckedChangeListener { _, id ->
            prefs.style = if (id == 1002) Prefs.STYLE_FILL else Prefs.STYLE_FIT_BLUR
            MediaArtListener.instance?.refresh(forceRedraw = true)
        }
        root.addView(styles)

        val dimLabel = text("Darken: ${prefs.dimPercent}%", 15f, top = 12)
        root.addView(dimLabel)
        root.addView(SeekBar(this).apply {
            max = 80
            progress = prefs.dimPercent
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { dimLabel.text = "Darken: $p%" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    prefs.dimPercent = progress
                    MediaArtListener.instance?.refresh(forceRedraw = true)
                }
            })
        })

        val delayLabel = text("Go back to normal ${prefs.restoreDelaySeconds} s after the music stops", 15f, top = 12)
        root.addView(delayLabel)
        root.addView(SeekBar(this).apply {
            max = 300
            progress = prefs.restoreDelaySeconds
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    delayLabel.text = "Go back to normal $p s after the music stops"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { prefs.restoreDelaySeconds = progress }
            })
        })

        // 4. Normal lock screen
        root.addView(header("4. Your normal lock screen"))
        root.addView(text("Android doesn't let apps read your current lock screen image, so pick the " +
            "image you want back when the music stops. If you skip this, the lock screen uses your " +
            "home screen wallpaper.", 14f))
        defaultStatus = text("", 15f, top = 6)
        root.addView(defaultStatus)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Pick image"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }, PICK_IMAGE)
            }
        })
        row.addView(Button(this).apply {
            text = "Clear"
            setOnClickListener { prefs.defaultWallpaperFile.delete(); updateUi() }
        })
        root.addView(row)

        // Status
        root.addView(header("Last activity"))
        lastStatus = text("", 14f)
        root.addView(lastStatus)
        root.addView(text("Tip: on Samsung, if covers stop updating after a while, go to Settings > " +
            "Apps > Album Art Lock Screen > Battery and choose Unrestricted.", 13f, top = 16))
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                prefs.defaultWallpaperFile.outputStream().use { input.copyTo(it) }
            }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't read that image", Toast.LENGTH_LONG).show()
        }
        updateUi()
    }

    private fun updateUi() {
        val granted = hasAccess()
        accessStatus.text = if (granted) "✓ Access granted. You're all set."
            else "Turn on \"Album Art Lock Screen\" in the next screen. It's needed to see which song is playing."
        accessButton.text = if (granted) "Notification access settings" else "Open notification access settings"
        if (granted && MediaArtListener.instance == null) {
            // Ask Android to (re)connect the listener, e.g. right after access was granted.
            NotificationListenerService.requestRebind(ComponentName(this, MediaArtListener::class.java))
        }
        defaultStatus.text = if (prefs.defaultWallpaperFile.exists()) "✓ Image saved" else "No image picked"
        lastStatus.text = prefs.lastStatus
    }

    private fun hasAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, MediaArtListener::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    private fun header(s: String) = text(s, 18f, bold = true, top = 24)

    private fun text(s: String, size: Float, bold: Boolean = false, top: Int = 0) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.START
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val PICK_IMAGE = 42
    }
}
