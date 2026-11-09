package com.surveymate

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RecordingPlayer(context: Context) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var track: AudioTrack? = null
    private var wavFile: RandomAccessFile? = null
    private var wavThread: Thread? = null
    @Volatile private var wavPlaying = false
    @Volatile private var wavPaused = false
    @Volatile private var wavOffset = 0L
    private var wavDataStart = 44L
    private var wavDataSize = 0
    private var wavSampleRate = 16_000
    private var wavDurationMs = 0
    private var wavGain = 1f
    private var playSampleRate = 48_000
    private var focusRequest: AudioFocusRequest? = null
    var currentId: Long? = null
        private set
    var lastPlayEmpty: Boolean = false
        private set
    var onTick: ((positionMs: Int, durationMs: Int) -> Unit)? = null
    var onState: ((playing: Boolean) -> Unit)? = null
    var onComplete: (() -> Unit)? = null

    val playing: Boolean
        get() = wavPlaying || mediaPlayer?.isPlaying == true

    val positionMs: Int
        get() {
            if (wavFile != null) {
                val bytes = wavOffset.coerceAtLeast(0L)
                return ((bytes * 1000L) / (wavSampleRate * 2L)).toInt()
            }
            return try {
                mediaPlayer?.currentPosition ?: 0
            } catch (_: Exception) {
                0
            }
        }

    val durationMs: Int
        get() {
            if (wavFile != null) return wavDurationMs
            return try {
                mediaPlayer?.duration?.coerceAtLeast(0) ?: 0
            } catch (_: Exception) {
                0
            }
        }

    fun play(id: Long, file: File): Boolean {
        lastPlayEmpty = false
        if (currentId == id && (mediaPlayer != null || wavFile != null)) {
            return resume()
        }
        stop()
        if (!file.exists() || file.length() < 128) return false
        ensureMediaVolume()
        if (file.name.endsWith(".wav", ignoreCase = true) && playWav(id, file)) return true
        return playEncoded(id, file)
    }

    fun pause() {
        wavPaused = true
        wavPlaying = false
        try {
            track?.pause()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {
        }
        handler.removeCallbacks(ticker)
        onState?.invoke(false)
    }

    fun resume(): Boolean {
        ensureMediaVolume()
        val encoded = mediaPlayer
        if (encoded != null) {
            return try {
                encoded.start()
                handler.post(ticker)
                onState?.invoke(true)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (wavFile == null) return false
        wavPaused = false
        wavPlaying = true
        try {
            track?.play()
        } catch (_: Exception) {
            return false
        }
        handler.post(ticker)
        onState?.invoke(true)
        return true
    }

    fun seekTo(positionMs: Int) {
        val encoded = mediaPlayer
        if (encoded != null) {
            try {
                encoded.seekTo(positionMs.coerceAtLeast(0))
            } catch (_: Exception) {
            }
            return
        }
        val bytes = (positionMs.toLong() * wavSampleRate * 2L / 1000L).coerceIn(0L, wavDataSize.toLong())
        wavOffset = bytes
        try {
            wavFile?.seek(wavDataStart + bytes)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        handler.removeCallbacks(ticker)
        wavPlaying = false
        wavPaused = false
        val worker = wavThread
        wavThread = null
        try {
            track?.pause()
            track?.stop()
        } catch (_: Exception) {
        }
        try {
            worker?.join(400)
        } catch (_: Exception) {
        }
        try {
            track?.release()
        } catch (_: Exception) {
        }
        track = null
        try {
            wavFile?.close()
        } catch (_: Exception) {
        }
        wavFile = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        currentId = null
        abandonFocus()
        onState?.invoke(false)
    }

    fun release() {
        stop()
        onTick = null
        onState = null
        onComplete = null
    }

    private fun playWav(id: Long, file: File): Boolean {
        return try {
            val raf = RandomAccessFile(file, "r")
            val header = parseWav(raf) ?: run {
                raf.close()
                return false
            }
            val peak = scanPeak(raf, header.first, header.second)
            if (peak < 8) {
                lastPlayEmpty = true
                raf.close()
                return false
            }
            wavFile = raf
            wavDataStart = header.first
            wavDataSize = header.second
            wavSampleRate = header.third
            wavGain = (22_000f / peak).coerceIn(1f, 48f)
            wavDurationMs = ((wavDataSize * 1000L) / (wavSampleRate * 2L)).toInt().coerceAtLeast(1)
            wavOffset = 0L
            raf.seek(wavDataStart)
            playSampleRate = 48_000
            val min = AudioTrack.getMinBufferSize(
                playSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(playSampleRate / 5 * 2)
            val next = AudioTrack.Builder()
                .setAudioAttributes(mediaAttributes())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(playSampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(min)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            next.setVolume(1f)
            next.play()
            track = next
            wavPaused = false
            wavPlaying = true
            currentId = id
            val worker = Thread({ streamWav(next, raf, min) }, "recording-playback")
            wavThread = worker
            worker.start()
            handler.post(ticker)
            onState?.invoke(true)
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    private fun streamWav(output: AudioTrack, raf: RandomAccessFile, bufferSize: Int) {
        val source = ByteArray(max(512, wavSampleRate / 10 * 2))
        val out = ByteArray(bufferSize.coerceAtLeast(playSampleRate / 10 * 2))
        while (wavPlaying || wavPaused) {
            if (wavPaused) {
                try {
                    Thread.sleep(40)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }
            val remaining = wavDataSize - wavOffset
            if (remaining <= 0) {
                wavPlaying = false
                wavPaused = true
                wavOffset = 0L
                try {
                    raf.seek(wavDataStart)
                    output.pause()
                } catch (_: Exception) {
                }
                handler.post {
                    onTick?.invoke(wavDurationMs, wavDurationMs)
                    onState?.invoke(false)
                    onComplete?.invoke()
                }
                continue
            }
            val toRead = min(source.size.toLong(), remaining).toInt()
            val read = try {
                raf.read(source, 0, toRead)
            } catch (_: Exception) {
                break
            }
            if (read <= 0) break
            wavOffset += read
            val converted = resample(source, read, out)
            try {
                var written = 0
                while (written < converted) {
                    val n = output.write(out, written, converted - written)
                    if (n <= 0) break
                    written += n
                }
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun resample(source: ByteArray, length: Int, dest: ByteArray): Int {
        val inSamples = length / 2
        if (inSamples <= 0) return 0
        val ratio = playSampleRate.toDouble() / wavSampleRate.toDouble()
        val outSamples = min((inSamples * ratio).toInt(), dest.size / 2)
        var i = 0
        while (i < outSamples) {
            val src = (i / ratio)
            val i0 = src.toInt().coerceIn(0, inSamples - 1)
            val i1 = (i0 + 1).coerceAtMost(inSamples - 1)
            val frac = (src - i0).toFloat()
            val s0 = sampleAt(source, i0 * 2)
            val s1 = sampleAt(source, i1 * 2)
            val mixed = (s0 + (s1 - s0) * frac) * wavGain
            val boosted = mixed.toInt().coerceIn(-32767, 32767)
            dest[i * 2] = (boosted and 0xff).toByte()
            dest[i * 2 + 1] = (boosted shr 8).toByte()
            i++
        }
        return outSamples * 2
    }

    private fun playEncoded(id: Long, file: File): Boolean {
        return try {
            val next = MediaPlayer()
            next.setAudioAttributes(mediaAttributes())
            next.setDataSource(file.absolutePath)
            next.prepare()
            next.setVolume(1f, 1f)
            next.setOnCompletionListener {
                handler.removeCallbacks(ticker)
                onTick?.invoke(durationMs, durationMs)
                onState?.invoke(false)
                onComplete?.invoke()
            }
            next.start()
            mediaPlayer = next
            currentId = id
            handler.post(ticker)
            onState?.invoke(true)
            true
        } catch (_: Exception) {
            false
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            onTick?.invoke(positionMs, durationMs)
            if (playing) handler.postDelayed(this, 200)
        }
    }

    private fun mediaAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
            .build()
    }

    private fun ensureMediaVolume() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (current < (max / 2)) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.9f).toInt().coerceAtLeast(1), 0)
                }
            }
            requestFocus()
        } catch (_: Exception) {
        }
    }

    private fun requestFocus() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(mediaAttributes())
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        try {
            val request = focusRequest
            focusRequest = null
            if (Build.VERSION.SDK_INT >= 26 && request != null) {
                audioManager.abandonAudioFocusRequest(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }

    private fun scanPeak(raf: RandomAccessFile, dataStart: Long, dataSize: Int): Int {
        val buf = ByteArray(8192)
        var peak = 0
        var left = dataSize
        raf.seek(dataStart)
        while (left > 0) {
            val n = raf.read(buf, 0, min(left, buf.size))
            if (n <= 0) break
            var i = 0
            while (i + 1 < n) {
                peak = max(peak, abs(sampleAt(buf, i)))
                i += 2
            }
            left -= n
        }
        return peak
    }

    private fun sampleAt(buffer: ByteArray, index: Int): Int {
        var sample = (buffer[index].toInt() and 0xff) or (buffer[index + 1].toInt() shl 8)
        if (sample > 32767) sample -= 65536
        return sample
    }

    private fun parseWav(raf: RandomAccessFile): Triple<Long, Int, Int>? {
        return try {
            val riff = ByteArray(12)
            raf.readFully(riff)
            if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") return null
            var sampleRate = 16_000
            var dataStart = 44L
            var dataSize = (raf.length() - 44L).toInt().coerceAtLeast(0)
            while (raf.filePointer + 8 <= raf.length()) {
                val id = ByteArray(4)
                raf.readFully(id)
                val size = readInt(raf)
                val name = String(id)
                val start = raf.filePointer
                when (name) {
                    "fmt " -> {
                        raf.skipBytes(4)
                        sampleRate = readInt(raf)
                    }
                    "data" -> {
                        dataStart = start
                        dataSize = if (size > 0) size else (raf.length() - start).toInt()
                        break
                    }
                }
                raf.seek(start + size)
            }
            Triple(dataStart, dataSize, sampleRate)
        } catch (_: Exception) {
            null
        }
    }

    private fun readInt(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
