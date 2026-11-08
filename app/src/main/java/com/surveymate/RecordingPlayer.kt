package com.surveymate

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile

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
    var currentId: Long? = null
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
        if (currentId == id && (mediaPlayer != null || wavFile != null)) {
            return resume()
        }
        stop()
        if (!file.exists() || file.length() < 128) return false
        ensureMediaVolume()
        return if (file.name.endsWith(".wav", ignoreCase = true)) {
            playWav(id, file)
        } else {
            playEncoded(id, file)
        }
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
                return playEncoded(id, file)
            }
            wavFile = raf
            wavDataStart = header.first
            wavDataSize = header.second
            wavSampleRate = header.third
            wavDurationMs = ((wavDataSize * 1000L) / (wavSampleRate * 2L)).toInt().coerceAtLeast(1)
            wavOffset = 0L
            raf.seek(wavDataStart)
            val min = AudioTrack.getMinBufferSize(
                wavSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(wavSampleRate / 5 * 2)
            val next = AudioTrack.Builder()
                .setAudioAttributes(mediaAttributes())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(wavSampleRate)
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
        val buffer = ByteArray(bufferSize)
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
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = try {
                raf.read(buffer, 0, toRead)
            } catch (_: Exception) {
                break
            }
            if (read <= 0) break
            wavOffset += read
            try {
                output.write(buffer, 0, read)
            } catch (_: Exception) {
                break
            }
        }
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
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max > 0 && current < (max / 4)) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.7f).toInt().coerceAtLeast(1), 0)
            }
        } catch (_: Exception) {
        }
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
                        dataSize = size
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
