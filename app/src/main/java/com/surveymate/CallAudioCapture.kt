package com.surveymate

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import java.io.File
import java.io.RandomAccessFile

class CallAudioCapture(context: Context) {
    data class Result(val file: File, val durationMs: Long)

    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val effects = mutableListOf<android.media.audiofx.AudioEffect>()

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var output: RandomAccessFile? = null
    private var outputFile: File? = null
    private var sampleRate = 16_000
    private var dataBytes = 0
    private var startedAt = 0L
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var routed = false

    val active: Boolean get() = running

    @Synchronized
    fun start(file: File): Boolean {
        if (running) return true
        dataBytes = 0
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.DEFAULT
        )
        prepareCallAudio()
        for (source in sources) {
            val opened = openRecord(source) ?: continue
            val primed = prime(opened, source == MediaRecorder.AudioSource.VOICE_CALL)
            if (primed == null) {
                closeRecord(opened)
                continue
            }
            if (!beginFile(file, opened.sampleRate)) {
                closeRecord(opened)
                continue
            }
            disableProcessing(opened)
            try {
                if (primed.isNotEmpty()) {
                    output?.write(primed)
                    dataBytes += primed.size
                }
            } catch (_: Exception) {
            }
            record = opened
            sampleRate = opened.sampleRate
            running = true
            startedAt = System.currentTimeMillis()
            val worker = Thread({ loop(opened) }, "call-audio-capture")
            thread = worker
            worker.start()
            return true
        }
        restoreCallAudio()
        return false
    }

    @Synchronized
    fun stop(): Result? {
        running = false
        val worker = thread
        thread = null
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        try {
            worker?.join(1_500)
        } catch (_: Exception) {
        }
        val rec = record
        record = null
        try {
            rec?.release()
        } catch (_: Exception) {
        }
        releaseEffects()
        val raf = output
        output = null
        val file = outputFile
        outputFile = null
        val bytes = dataBytes
        val duration = if (startedAt > 0L) System.currentTimeMillis() - startedAt else 0L
        startedAt = 0L
        restoreCallAudio()
        if (raf == null || file == null) return null
        return try {
            writeHeader(raf, bytes, sampleRate)
            raf.close()
            if (!file.exists() || bytes < sampleRate) {
                file.delete()
                null
            } else {
                Result(file, duration.coerceAtLeast((bytes * 1000L) / (sampleRate * 2L)))
            }
        } catch (_: Exception) {
            try {
                raf.close()
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun loop(recorder: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            try {
                recorder.startRecording()
            } catch (_: Exception) {
                running = false
                return
            }
        }
        val buffer = ByteArray(bufferSize(recorder).coerceAtLeast(sampleRate / 5 * 2))
        var peak = 1
        while (running) {
            val read = try {
                recorder.read(buffer, 0, buffer.size)
            } catch (_: Exception) {
                break
            }
            if (read <= 0) continue
            peak = maxOf(peak, peakAmplitude(buffer, read))
            val gain = when {
                peak < 400 -> 12f
                peak < 1_200 -> 8f
                peak < 3_000 -> 5f
                peak < 8_000 -> 2.5f
                else -> 1.4f
            }
            amplify(buffer, read, gain)
            try {
                output?.write(buffer, 0, read)
                dataBytes += read
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun bufferSize(recorder: AudioRecord): Int {
        val size = AudioRecord.getMinBufferSize(
            recorder.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return if (size > 0) size else recorder.sampleRate / 5 * 2
    }

    private fun closeRecord(recorder: AudioRecord) {
        try {
            recorder.stop()
        } catch (_: Exception) {
        }
        try {
            recorder.release()
        } catch (_: Exception) {
        }
    }

    private fun openRecord(source: Int): AudioRecord? {
        val rates = intArrayOf(44_100, 16_000, 8_000)
        for (rate in rates) {
            val min = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (min <= 0) continue
            try {
                val recorder = AudioRecord(
                    source,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    min * 2
                )
                if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
                recorder.release()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun beginFile(file: File, rate: Int): Boolean {
        return try {
            if (file.exists()) file.delete()
            val raf = RandomAccessFile(file, "rw")
            writeHeader(raf, 0, rate)
            output = raf
            outputFile = file
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun prepareCallAudio() {
        try {
            previousMode = audioManager.mode
            @Suppress("DEPRECATION")
            previousSpeaker = audioManager.isSpeakerphoneOn
            routed = true
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        } catch (_: Exception) {
        }
    }

    private fun restoreCallAudio() {
        if (!routed) return
        routed = false
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = previousSpeaker
            audioManager.mode = previousMode
        } catch (_: Exception) {
        }
    }

    private fun prime(recorder: AudioRecord, requireSignal: Boolean): ByteArray? {
        return try {
            recorder.startRecording()
            val chunk = ByteArray(bufferSize(recorder).coerceAtLeast(2_048))
            val collected = java.io.ByteArrayOutputStream()
            var peak = 0
            val until = System.currentTimeMillis() + 350
            while (System.currentTimeMillis() < until) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    collected.write(chunk, 0, read)
                    peak = maxOf(peak, peakAmplitude(chunk, read))
                }
            }
            if (requireSignal && peak < 120) null else collected.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun disableProcessing(recorder: AudioRecord) {
        releaseEffects()
        val session = recorder.audioSessionId
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(session)?.let {
                    it.enabled = false
                    effects.add(it)
                }
            }
        } catch (_: Exception) {
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(session)?.let {
                    it.enabled = false
                    effects.add(it)
                }
            }
        } catch (_: Exception) {
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(session)?.let {
                    it.enabled = true
                    effects.add(it)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseEffects() {
        effects.forEach { effect ->
            try {
                effect.release()
            } catch (_: Exception) {
            }
        }
        effects.clear()
    }

    private fun peakAmplitude(buffer: ByteArray, length: Int): Int {
        var peak = 0
        var i = 0
        while (i + 1 < length) {
            var sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
            if (sample > 32767) sample -= 65536
            val abs = if (sample < 0) -sample else sample
            if (abs > peak) peak = abs
            i += 2
        }
        return peak
    }

    private fun amplify(buffer: ByteArray, length: Int, gain: Float) {
        if (gain <= 1.01f) return
        var i = 0
        while (i + 1 < length) {
            var sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
            if (sample > 32767) sample -= 65536
            val boosted = (sample * gain).toInt().coerceIn(-32767, 32767)
            buffer[i] = (boosted and 0xff).toByte()
            buffer[i + 1] = (boosted shr 8).toByte()
            i += 2
        }
    }

    companion object {
        fun writeHeader(raf: RandomAccessFile, dataBytes: Int, sampleRate: Int) {
            val byteRate = sampleRate * 2
            raf.seek(0)
            raf.writeBytes("RIFF")
            writeInt(raf, 36 + dataBytes)
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            writeInt(raf, 16)
            writeShort(raf, 1)
            writeShort(raf, 1)
            writeInt(raf, sampleRate)
            writeInt(raf, byteRate)
            writeShort(raf, 2)
            writeShort(raf, 16)
            raf.writeBytes("data")
            writeInt(raf, dataBytes)
        }

        private fun writeInt(raf: RandomAccessFile, value: Int) {
            raf.write(value and 0xff)
            raf.write(value shr 8 and 0xff)
            raf.write(value shr 16 and 0xff)
            raf.write(value shr 24 and 0xff)
        }

        private fun writeShort(raf: RandomAccessFile, value: Int) {
            raf.write(value and 0xff)
            raf.write(value shr 8 and 0xff)
        }
    }
}
