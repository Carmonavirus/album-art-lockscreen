package com.marc.albumartlock

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
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
    private lateinit var preview: ImageView
    private lateinit var enabledSwitch: Switch
    private lateinit var lookupLog: TextView

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
        root.addView(text("Tip: add the \"Album art\" tile to your quick settings panel (swipe down, " +
            "tap the pencil/edit button) to switch this on and off quickly, even from the lock screen.", 13f))
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            root.addView(Button(this).apply {
                text = "Add on/off tile to quick settings"
                setOnClickListener {
                    getSystemService(android.app.StatusBarManager::class.java)?.requestAddTileService(
                        ComponentName(this@MainActivity, ToggleTileService::class.java),
                        "Album art",
                        android.graphics.drawable.Icon.createWithResource(this@MainActivity, R.drawable.ic_tile),
                        mainExecutor,
                    ) { }
                }
            })
        }
        enabledSwitch = Switch(this).apply {
            text = "Show album art on the lock screen"
            textSize = 16f
            isChecked = prefs.enabled
            setOnCheckedChangeListener { _, on ->
                prefs.enabled = on
                MediaArtListener.instance?.refresh(forceRedraw = on)
            }
        }
        root.addView(enabledSwitch)

        // 3. Look
        root.addView(header("3. Customize the look"))
        root.addView(text("Changes show up in the preview right away, and on your lock screen " +
            "the next time it updates (immediately if a song is playing).", 14f))
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(200), WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12); bottomMargin = dp(8)
            }
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.BLACK) }
            clipToOutline = true
        }
        root.addView(preview)

        root.addView(subheader("Layout"))
        root.addView(choice(listOf("Rounded cover", "Cover fills the screen"), prefs.layout) {
            prefs.layout = it; lookChanged()
        })

        root.addView(subheader("Background"))
        root.addView(choice(listOf("Cover colour (Samsung)", "Blurred cover", "Black"), prefs.background) {
            prefs.background = it; lookChanged()
        })

        root.addView(subheader("Cover"))
        slider("Position from top", 5, 60, "%", { prefs.coverTopPercent }, { prefs.coverTopPercent = it }, root)
        slider("Size", 40, 100, "%", { prefs.coverSizePercent }, { prefs.coverSizePercent = it }, root)
        slider("Corner roundness", 0, 20, "", { prefs.cornerPercent }, { prefs.cornerPercent = it }, root)

        root.addView(subheader("Text under the cover"))
        toggle("Song title", { prefs.showTitle }, { prefs.showTitle = it }, root)
        toggle("Artist", { prefs.showArtist }, { prefs.showArtist = it }, root)
        toggle("Album", { prefs.showAlbum }, { prefs.showAlbum = it }, root)
        slider("Text size", 60, 160, "%", { prefs.textScalePercent }, { prefs.textScalePercent = it }, root)
        root.addView(text("Text position", 15f, top = 8))
        root.addView(choice(listOf("Below the cover", "Above the cover", "On the cover (bottom)"), prefs.textPosition) {
            prefs.textPosition = it; lookChanged()
        })
        slider("Space between cover and text", 0, 200, "", { prefs.textGapTenths }, { prefs.textGapTenths = it }, root)
        root.addView(text("Text alignment", 15f, top = 8))
        root.addView(choice(listOf("Centred", "Left"), prefs.textAlign) {
            prefs.textAlign = it; lookChanged()
        })

        root.addView(subheader("Background brightness"))
        slider("Darken", 0, 80, "%", { prefs.dimPercent }, { prefs.dimPercent = it }, root)

        root.addView(Button(this).apply {
            text = "Reset to Samsung look"
            setOnClickListener { prefs.resetLook(); recreate(); MediaArtListener.instance?.refresh(forceRedraw = true) }
        })
        root.addView(text("Music controls: the wallpaper is a still image, so buttons drawn on it couldn't be " +
            "tapped. Samsung's own player at the bottom of the lock screen keeps working as usual.", 13f, top = 8))

        root.addView(header("Cover quality"))
        toggle("Find HD covers online", { prefs.hdLookup }, { prefs.hdLookup = it }, root)
        root.addView(text("Music apps often share only a small cover, which looks blurry full screen. " +
            "With this on, the app looks the song up in the iTunes and Deezer catalogues (song title and artist " +
            "only) and uses its high-resolution cover first. A search with a single result uses it; with several, one has to match exactly. Otherwise it uses the music app's own cover.", 13f))

        root.addView(subheader("Last cover search"))
        lookupLog = text("", 12f).apply { setTypeface(Typeface.MONOSPACE) ; setTextIsSelectable(true) }
        root.addView(lookupLog)
        root.addView(Button(this).apply {
            text = "Search again for the current song"
            setOnClickListener {
                val l = MediaArtListener.instance
                if (l == null) {
                    Toast.makeText(this@MainActivity, "Notification access is off", Toast.LENGTH_SHORT).show()
                } else {
                    l.refresh(forceRedraw = true)
                    lookupLog.text = "Searching…"
                    lookupLog.postDelayed({ updateUi() }, 6000)
                }
            }
        })

        root.addView(header("Timing"))
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
        updatePreview()
    }

    private fun lookChanged() {
        updatePreview()
        MediaArtListener.instance?.refresh(forceRedraw = true)
    }

    /** Draws the current look with the last song played (or a placeholder) at a third of screen size. */
    private fun updatePreview() {
        val metrics = resources.displayMetrics
        val sw = minOf(metrics.widthPixels, metrics.heightPixels)
        val sh = maxOf(metrics.widthPixels, metrics.heightPixels)
        val w = sw / 3
        val h = (w * (sh.toFloat() / sw) * 1.06f).toInt() // a bit taller: displayMetrics excludes the bars
        val art = prefs.lastCoverFile.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            ?: placeholderArt()
        val bmp = ArtRenderer.render(art, w, h, prefs.look(prefs.lastTitle, prefs.lastArtist, prefs.lastAlbum))
        // Faint clock where Samsung draws it, so you can see what the cover has to avoid.
        Canvas(bmp).drawText("12:45", w / 2f, h * 0.235f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            textSize = w * 0.22f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        })
        preview.setImageBitmap(bmp)
    }

    private fun placeholderArt(): Bitmap {
        val b = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRect(0f, 0f, 300f, 300f, Paint().apply {
            shader = LinearGradient(0f, 0f, 300f, 300f, Color.rgb(200, 40, 60), Color.rgb(60, 20, 120), Shader.TileMode.CLAMP)
        })
        return b
    }

    private fun choice(options: List<String>, selected: Int, onPick: (Int) -> Unit): RadioGroup {
        val group = RadioGroup(this)
        // Unique ids, so several groups on one screen don't clash.
        val ids = options.map { android.view.View.generateViewId() }
        options.forEachIndexed { i, label ->
            group.addView(RadioButton(this).apply { id = ids[i]; text = label })
        }
        group.check(ids[selected.coerceIn(0, ids.size - 1)])
        group.setOnCheckedChangeListener { _, id -> ids.indexOf(id).takeIf { it >= 0 }?.let(onPick) }
        return group
    }

    private fun slider(label: String, min: Int, max: Int, suffix: String, get: () -> Int, set: (Int) -> Unit, parent: LinearLayout) {
        val tv = text("$label: ${get()}$suffix", 15f, top = 8)
        parent.addView(tv)
        parent.addView(SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = get()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    tv.text = "$label: $p$suffix"
                    if (fromUser) { set(p); updatePreview() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { set(progress); lookChanged() }
            })
        })
    }

    private fun toggle(label: String, get: () -> Boolean, set: (Boolean) -> Unit, parent: LinearLayout) {
        parent.addView(Switch(this).apply {
            text = label
            textSize = 15f
            isChecked = get()
            setOnCheckedChangeListener { _, on -> set(on); lookChanged() }
        })
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
        lookupLog.text = prefs.lookupLog
        // The tile may have changed this while the app was in the background.
        if (enabledSwitch.isChecked != prefs.enabled) enabledSwitch.isChecked = prefs.enabled
    }

    private fun hasAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, MediaArtListener::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    private fun header(s: String) = text(s, 18f, bold = true, top = 24)
    private fun subheader(s: String) = text(s, 15f, bold = true, top = 14)

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
