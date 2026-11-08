package com.surveymate

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.abs

object OverlayController {
    @Volatile
    var visible: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val enableTouchesRunnable = Runnable { enableTouches() }
    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshRecordButton()
            if (CallRecordingController.recording && overlayView != null) {
                mainHandler.postDelayed(this, 500)
            }
        }
    }
    private val recordingListener: (Boolean) -> Unit = {
        mainHandler.post { refreshRecordButton() }
    }
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var currentSource: String = Survey.SOURCE_CALL
    private var attachedAt: Long = 0L
    private const val CLICK_GUARD_MS = 900L

    fun show(context: Context, source: String = Survey.SOURCE_CALL) {
        val app = context.applicationContext
        currentSource = source
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
            return
        }
        val service = Intent(app, QuickAddOverlayService::class.java)
        try {
            ContextCompat.startForegroundService(app, service)
        } catch (_: Exception) {
            mainHandler.post { attach(app) }
        }
        mainHandler.post { attach(app) }
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        mainHandler.post { detach() }
        try {
            app.stopService(Intent(app, QuickAddOverlayService::class.java))
        } catch (_: Exception) {
        }
    }

    fun hideIfSource(context: Context, source: String) {
        if (currentSource == source) hide(context)
    }

    fun attachFromService(context: Context) {
        mainHandler.post { attach(context.applicationContext) }
    }

    fun detachFromService() {
        mainHandler.post { detach() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach(app: Context) {
        if (overlayView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) return
        val themed = ContextThemeWrapper(app, R.style.Theme_SurveyMate)
        windowManager = app.getSystemService(WindowManager::class.java)
        val view = LayoutInflater.from(themed).inflate(R.layout.quick_survey_overlay, null)
        overlayView = view
        attachedAt = System.currentTimeMillis()
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        var tracking = false
        var pressedRecord = false
        val dragListener = View.OnTouchListener { _, event ->
            val layout = params ?: return@OnTouchListener false
            val recordButton = view.findViewById<View>(R.id.recordButton)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (System.currentTimeMillis() - attachedAt < CLICK_GUARD_MS) {
                        tracking = false
                        return@OnTouchListener true
                    }
                    downX = event.rawX
                    downY = event.rawY
                    startX = layout.x
                    startY = layout.y
                    dragged = false
                    tracking = true
                    pressedRecord = hitOnScreen(recordButton, event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return@OnTouchListener true
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) dragged = true
                    layout.x = (startX - dx).coerceAtLeast(0)
                    layout.y = (startY + dy).coerceAtLeast(0)
                    try {
                        windowManager?.updateViewLayout(view, layout)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val canClick = tracking && !dragged &&
                        System.currentTimeMillis() - attachedAt >= CLICK_GUARD_MS
                    tracking = false
                    if (canClick) {
                        if (pressedRecord) {
                            CallRecordingController.toggle(app)
                        } else {
                            openAddSurvey(app)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    tracking = false
                    true
                }
                else -> false
            }
        }
        view.setOnTouchListener(dragListener)
        CallRecordingController.addListener(recordingListener)
        refreshRecordButton()

        try {
            windowManager?.addView(view, params)
            visible = true
            mainHandler.removeCallbacks(enableTouchesRunnable)
            mainHandler.postDelayed(enableTouchesRunnable, CLICK_GUARD_MS)
        } catch (_: Exception) {
            overlayView = null
            visible = false
            CallRecordingController.removeListener(recordingListener)
        }
    }

    private fun hitOnScreen(child: View, event: MotionEvent): Boolean {
        val loc = IntArray(2)
        child.getLocationOnScreen(loc)
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        return x >= loc[0] && x <= loc[0] + child.width &&
            y >= loc[1] && y <= loc[1] + child.height
    }

    private fun refreshRecordButton() {
        val view = overlayView ?: return
        val button = view.findViewById<View>(R.id.recordButton)
        val label = view.findViewById<TextView>(R.id.recordLabel)
        val icon = view.findViewById<ImageView>(R.id.recordIcon)
        val recording = CallRecordingController.recording
        button.setBackgroundResource(
            if (recording) R.drawable.bg_fab_overlay_recording else R.drawable.bg_fab_overlay_record
        )
        icon.setImageResource(if (recording) R.drawable.ic_stop else R.drawable.ic_mic)
        label.text = if (recording) {
            view.context.getString(R.string.stop_recording, CallRecordingController.formatElapsed())
        } else {
            view.context.getString(R.string.record_call)
        }
        mainHandler.removeCallbacks(tickRunnable)
        if (recording) mainHandler.postDelayed(tickRunnable, 500)
    }

    private fun enableTouches() {
        val view = overlayView ?: return
        val layout = params ?: return
        layout.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        try {
            windowManager?.updateViewLayout(view, layout)
        } catch (_: Exception) {
        }
    }

    private fun openAddSurvey(app: Context) {
        val (phone, name) = CallNumberTracker.resolve(app)
        val launch = Intent(app, AddSurveyActivity::class.java).apply {
            putExtra(AddSurveyActivity.EXTRA_PHONE, phone)
            putExtra(AddSurveyActivity.EXTRA_NAME, name)
            putExtra(AddSurveyActivity.EXTRA_SOURCE, currentSource)
            putExtra(AddSurveyActivity.EXTRA_FROM_LIVE_CALL, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            app.startActivity(launch)
        } catch (_: Exception) {
            return
        }
        if (!CallRecordingController.recording) hide(app)
    }

    private fun detach() {
        mainHandler.removeCallbacks(enableTouchesRunnable)
        mainHandler.removeCallbacks(tickRunnable)
        CallRecordingController.removeListener(recordingListener)
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        visible = false
    }
}
