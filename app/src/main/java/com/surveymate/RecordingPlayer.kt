package com.surveymate

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileInputStream

class RecordingPlayer(context: Context) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var sourceStream: FileInputStream? = null
    private var focusRequest: AudioFocusRequest? = null
    private var previousSpeaker = false
    var currentId: Long? = null
        private set
    var onTick: ((positionMs: Int, durationMs: Int) -> Unit)? = null
    var onState: ((playing: Boolean) -> Unit)? = null
    var onComplete: (() -> Unit)? = null

    val playing: Boolean get() = player?.isPlaying == true
    val positionMs: Int get() = try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 }
    val durationMs: Int get() = try { player?.duration?.coerceAtLeast(0) ?: 0 } catch (_: Exception) { 0 }

    fun play(id: Long, file: File): Boolean {
        if (currentId == id && player != null) {
            return resume()
        }
        stop()
        if (!file.exists() || file.length() < 256) return false
        return try {
            routeToSpeaker()
            requestFocus()
            val next = MediaPlayer()
            next.setAudioAttributes(mediaAttributes())
            val stream = FileInputStream(file)
            sourceStream = stream
            try {
                next.setDataSource(stream.fd)
            } catch (_: Exception) {
                next.setDataSource(file.absolutePath)
            }
            next.prepare()
            next.setVolume(1f, 1f)
            next.setOnCompletionListener {
                handler.removeCallbacks(ticker)
                onTick?.invoke(durationMs, durationMs)
                onState?.invoke(false)
                onComplete?.invoke()
            }
            next.start()
            player = next
            currentId = id
            handler.post(ticker)
            onState?.invoke(true)
            true
        } catch (_: Exception) {
            releasePlayer()
            false
        }
    }

    fun pause() {
        try {
            player?.pause()
        } catch (_: Exception) {
        }
        handler.removeCallbacks(ticker)
        onState?.invoke(false)
    }

    fun resume(): Boolean {
        val next = player ?: return false
        return try {
            routeToSpeaker()
            requestFocus()
            next.start()
            handler.post(ticker)
            onState?.invoke(true)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            player?.seekTo(positionMs.coerceAtLeast(0))
        } catch (_: Exception) {
        }
    }

    fun stop() {
        handler.removeCallbacks(ticker)
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        releasePlayer()
        abandonFocus()
        restoreAudio()
        currentId = null
        onState?.invoke(false)
    }

    fun release() {
        stop()
        onTick = null
        onState = null
        onComplete = null
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (player == null) return
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

    private fun routeToSpeaker() {
        try {
            @Suppress("DEPRECATION")
            previousSpeaker = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        } catch (_: Exception) {
        }
    }

    private fun restoreAudio() {
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = previousSpeaker
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    private fun requestFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(mediaAttributes())
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
        focusRequest = null
    }

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            sourceStream?.close()
        } catch (_: Exception) {
        }
        sourceStream = null
    }
}
