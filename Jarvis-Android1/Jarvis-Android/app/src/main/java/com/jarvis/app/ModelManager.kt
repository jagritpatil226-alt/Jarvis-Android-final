package com.jarvis.app

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Downloads the Vosk model zip from Google Drive on first launch and unzips it into internal storage. */
object ModelManager {
    const val FILE_ID = "1DWYr_jF-ffRq3lAzl7-VnfGbRX7oJqw-"   // vosk-model-small-en-in-0.4.zip
    private const val SIZE_GUESS = 37_573_330L

    private fun dir(c: Context) = File(c.filesDir, "model")
    fun modelRoot(c: Context): File? =
        dir(c).walkTopDown().firstOrNull { it.isDirectory && File(it, "am").isDirectory && File(it, "conf").isDirectory }

    fun ensure(c: Context, onProgress: (String, Int) -> Unit) {
        if (File(dir(c), ".ready").exists() && modelRoot(c) != null) return
        val zip = File(c.cacheDir, "model.zip")
        val con = URL("https://drive.usercontent.google.com/download?id=$FILE_ID&export=download&confirm=t")
            .openConnection() as HttpURLConnection
        con.connectTimeout = 20000; con.readTimeout = 30000
        if ((con.contentType ?: "").startsWith("text/html"))
            throw IllegalStateException("Drive blocked the download. Set the zip to 'Anyone with the link'.")
        val total = if (con.contentLengthLong > 0) con.contentLengthLong else SIZE_GUESS
        con.inputStream.use { i ->
            zip.outputStream().use { o ->
                val buf = ByteArray(65536); var n = 0; var done = 0L
                while (i.read(buf).also { n = it } > 0) {
                    o.write(buf, 0, n); done += n
                    onProgress("DOWNLOADING VOICE MODEL", (done * 100 / total).toInt().coerceAtMost(99))
                }
            }
        }
        onProgress("UNPACKING MODEL", 100)
        val out = dir(c); out.deleteRecursively(); out.mkdirs()
        val base = out.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val f = File(out, e.name)
                if (!f.canonicalPath.startsWith(base)) throw SecurityException("Bad zip entry")
                if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                e = z.nextEntry
            }
        }
        zip.delete()
        if (modelRoot(c) == null) throw IllegalStateException("Model folder not found inside the zip")
        File(out, ".ready").writeText("ok")
    }
}
