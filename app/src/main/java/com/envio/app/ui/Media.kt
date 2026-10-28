package com.envio.app.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File

object Media {
    fun open(ctx: Context, path: String?, mime: String?) {
        if (path == null) return
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(path))
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No hay una app para abrir este archivo", Toast.LENGTH_SHORT).show()
        }
    }
}

fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format("%.1f MB", b / 1024.0 / 1024.0)
}

object Player {
    private var mp: MediaPlayer? = null
    var current by mutableStateOf<String?>(null)
        private set

    fun toggle(path: String) {
        if (current == path) {
            stop(); return
        }
        stop()
        try {
            mp = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            current = path
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        try { mp?.stop() } catch (_: Exception) {}
        mp?.release()
        mp = null
        current = null
    }
}

class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null

    @Suppress("DEPRECATION")
    fun start(): Boolean {
        return try {
            val f = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
            file = f
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            true
        } catch (e: Exception) {
            rec?.release(); rec = null
            false
        }
    }

    fun stop(): File? {
        val r = rec ?: return null
        rec = null
        return try {
            r.stop()
            r.release()
            file
        } catch (e: Exception) {
            r.release()
            file?.delete()
            null
        }
    }
}
