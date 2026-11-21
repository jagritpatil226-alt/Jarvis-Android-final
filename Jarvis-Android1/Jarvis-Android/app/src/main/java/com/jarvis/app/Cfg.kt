package com.jarvis.app

import android.content.Context

/** Link + secret: baked in at build time (GitHub secrets), or typed once in the app if the build had none. */
object Cfg {
    private fun p(c: Context) = c.getSharedPreferences("j", Context.MODE_PRIVATE)
    fun url(c: Context): String = BuildConfig.JARVIS_URL.ifBlank { p(c).getString("url", "") ?: "" }
    fun key(c: Context): String = BuildConfig.JARVIS_KEY.ifBlank { p(c).getString("key", "") ?: "" }
    fun ok(c: Context) = url(c).isNotBlank() && key(c).isNotBlank()
}
