package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var hud: HudView
    private lateinit var form: LinearLayout
    private val ui = Handler(Looper.getMainLooper())
    private var started = false
    private val prefs by lazy { getSharedPreferences("j", MODE_PRIVATE) }

    private fun field(h: String) = EditText(this).apply {
        hint = h; setHintTextColor(0x8819E3FF.toInt()); setTextColor(0xFF19E3FF.toInt())
        setBackgroundColor(0xFF0B1626.toInt()); setPadding(30, 30, 30, 30); inputType = InputType.TYPE_CLASS_TEXT
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = 0xFF05080F.toInt(); window.navigationBarColor = 0xFF05080F.toInt()
        hud = HudView(this)
        val root = FrameLayout(this); root.addView(hud)
        val u = field("APPS SCRIPT LINK (ends with /exec)"); val k = field("SECRET KEY")
        form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 0, 60, 90); visibility = View.GONE }
        form.addView(u); form.addView(k)
        form.addView(Button(this).apply {
            text = "SAVE (ONLY ONCE)"
            setOnClickListener {
                prefs.edit().putString("url", u.text.toString().trim()).putString("key", k.text.toString().trim()).apply()
                form.visibility = View.GONE; boot()
            }
        })
        root.addView(form, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
        tick()
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { boot() }
    override fun onResume() { super.onResume(); boot() }

    private fun state(s: String) = prefs.edit().putString("state", s).apply()

    private fun boot() {
        if (started) { chain(); return }
        if (!Cfg.ok(this)) { form.visibility = View.VISIBLE; state("CREDENTIALS NEEDED"); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state("MIC PERMISSION NEEDED"); return
        }
        started = true
        Thread {
            try {
                ModelManager.ensure(this) { s, pc -> state(s); hud.pct = pc }
                hud.pct = -1
                ui.post { startForegroundService(Intent(this, JarvisService::class.java)); chain() }
            } catch (e: Exception) { hud.pct = -1; state("ERROR: " + (e.message ?: e.javaClass.simpleName)); started = false }
        }.start()
    }

    /** One-time system permissions, asked one per return to the app. */
    private fun chain() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("ab", false)) {
            prefs.edit().putBoolean("ab", true).apply()
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } else if (!Settings.canDrawOverlays(this) && !prefs.getBoolean("ao", false)) {
            prefs.edit().putBoolean("ao", true).apply()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun tick() {
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        hud.status = prefs.getString("state", "STANDBY") ?: "STANDBY"
        hud.rows = listOf(
            "HEARD" to (prefs.getString("heard", "-") ?: "-"),
            "LAST CMD" to (prefs.getString("last", "-") ?: "-"),
            "POWER" to "$lvl%",
            "CLOCK" to SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
            "VOICE CORE" to "VOSK EN-IN OFFLINE",
            "UPLINK" to if (Cfg.ok(this)) "SECURED" else "NOT SET"
        )
        ui.postDelayed({ tick() }, 1000)
    }
}
