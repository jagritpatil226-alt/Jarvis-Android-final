package com.jarvis.app

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.*

class JarvisService : Service(), RecognitionListener {
    private var speech: SpeechService? = null
    private var tts: TextToSpeech? = null
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var inited = false
    private var awaiting = false
    private val idle = "LISTENING FOR JARVIS"
    private val expire = Runnable { awaiting = false; state(idle) }
    private val wake = listOf("jarvis", "jarvas", "jervis", "jarves", "jar vis")

    override fun onBind(i: Intent?): IBinder? = null

    private fun state(s: String) = prefs.edit().putString("state", s).apply()

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (inited) return START_STICKY
        inited = true
        prefs = getSharedPreferences("j", MODE_PRIVATE)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis").setContentTitle("Jarvis is listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) { tts?.language = Locale("hi", "IN"); tts?.setPitch(0.9f); tts?.setSpeechRate(0.95f) }
        }
        state("LOADING VOICE CORE")
        Thread {
            try {
                val root = ModelManager.modelRoot(this) ?: throw IllegalStateException("model missing")
                val rec = Recognizer(Model(root.absolutePath), 16000.0f)
                speech = SpeechService(rec, 16000.0f)
                speech?.startListening(this)
                state(idle)
            } catch (e: Exception) { state("ERROR: " + (e.message ?: "voice core")) }
        }.start()
        return START_STICKY
    }

    // ---- speech in / out ----
    private fun talk(t: String, done: (() -> Unit)? = null) {
        if (tts == null) { done?.invoke(); return }
        speech?.setPause(true)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onDone(u: String?) { ui.post { back(); done?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(u: String?) { ui.post { back(); done?.invoke() } }
        })
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    private fun back() { try { speech?.reset(); speech?.setPause(false) } catch (e: Exception) {} }

    override fun onResult(h: String?) {
        val text = try { JSONObject(h ?: "{}").optString("text") } catch (e: Exception) { "" }
        if (text.isBlank()) return
        prefs.edit().putString("heard", text).apply()
        ui.post {
            if (awaiting) { ui.removeCallbacks(expire); awaiting = false; handle(text) }
            else {
                val w = wake.firstOrNull { text.contains(it) } ?: return@post
                val rest = text.substringAfter(w).trim()
                if (rest.length > 2) handle(rest)
                else { awaiting = true; ui.postDelayed(expire, 10000); state("AWAITING COMMAND"); talk("जी सर?") }
            }
        }
    }
    override fun onPartialResult(h: String?) {}
    override fun onFinalResult(h: String?) {}
    override fun onError(e: Exception?) { state("MIC ERROR: " + (e?.message ?: "")) }
    override fun onTimeout() {}

    // ---- commands ----
    private fun handle(h: String) {
        val t = h.lowercase()
        state("PROCESSING"); prefs.edit().putString("last", t.take(24)).apply()
        when {
            t.contains("play") -> play(t)
            t.contains("office") -> brief("office", t)
            listOf("morning", "brief", "lead", "status").any { t.contains(it) } -> brief("morning", t)
            else -> talk("सर, मैं briefing, office mode और गाने समझता हूँ.") { state(idle) }
        }
    }

    private fun play(t: String) {
        val q = t.substringAfter("play").replace("on spotify", "").trim()
        if (q.isBlank()) { talk("कौन सा गाना, सर?") { state(idle) }; return }
        try {
            startActivity(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage("com.spotify.music")
                .putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            log(t, "spotify: $q"); talk("ठीक है सर.") { state(idle) }
        } catch (e: Exception) { talk("Spotify नहीं खुल पाया.") { state(idle) } }
    }

    private fun brief(mode: String, heard: String) {
        Thread {
            val said = try {
                val u = Cfg.url(this) + "?mode=$mode&key=" + URLEncoder.encode(Cfg.key(this), "UTF-8")
                JSONObject(URL(u).readText()).getString("say")
            } catch (e: Exception) { "सर, leads लोड नहीं हुए. Internet या link check करिए." }
            log(heard, said)
            ui.post { talk(said) { state(idle) } }
        }.start()
    }

    private fun log(heard: String, said: String) {
        val f = File(filesDir, "history.txt")
        val l = (if (f.exists()) f.readLines() else listOf()) + "${Date()} | $heard | ${said.take(120)}"
        f.writeText(l.takeLast(50).joinToString("\n"))
    }

    override fun onDestroy() {
        try { speech?.stop(); speech?.shutdown() } catch (e: Exception) {}
        tts?.shutdown(); super.onDestroy()
    }
}
