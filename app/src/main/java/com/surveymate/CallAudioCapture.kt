package com.surveymate

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class CallAudioCapture(context: Context) {
    data class Result(val file: File, val durationMs: Long)

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L

    @Volatile
    private var running = false

    val active: Boolean get() = running

    companion object {
        fun testMic(context: Context, durationMs: Long = 1_500L): Boolean {
            val dir = File(context.cacheDir, "mic_test").apply { mkdirs() }
            val file = File(dir, "mic_test_${System.currentTimeMillis()}.m4a")
            val recorder = MediaRecorder()
            return try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioSamplingRate(44_100)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                recorder.start()
                Thread.sleep(durationMs)
                recorder.stop()
                val ok = file.exists() && file.length() > 1024L
                if (!ok) file.delete()
                ok
            } catch (_: Exception) {
                file.delete()
                false
            } finally {
                try {
                    recorder.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    @Synchronized
    fun start(file: File): Boolean {
        if (running) return true
        return try {
            val next = MediaRecorder()
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(44_100)
            next.setAudioEncodingBitRate(128_000)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
            recorder = next
            outputFile = file
            startedAt = System.currentTimeMillis()
            running = true
            true
        } catch (_: Exception) {
            stopQuiet()
            false
        }
    }

    @Synchronized
    fun stop(): Result? {
        running = false
        val rec = recorder
        recorder = null
        val out = outputFile
        outputFile = null
        val start = startedAt
        startedAt = 0L
        return try {
            rec?.stop()
            val duration = if (start > 0L) System.currentTimeMillis() - start else 0L
            if (out != null && out.exists() && out.length() > 1024L) {
                Result(out, duration.coerceAtLeast(1000L))
            } else {
                out?.delete()
                null
            }
        } catch (_: Exception) {
            out?.delete()
            null
        } finally {
            try {
                rec?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun stopQuiet() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile = null
        startedAt = 0L
        running = false
    }
}
