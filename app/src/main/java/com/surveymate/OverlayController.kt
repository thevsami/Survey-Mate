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
import androidx.core.content.ContextCompat
import kotlin.math.abs

object OverlayController {
    @Volatile
    var visible: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val enableTouchesRunnable = Runnable { enableTouches() }
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
        val dragListener = View.OnTouchListener { _, event ->
            val layout = params ?: return@OnTouchListener false
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
                    if (canClick) openAddSurvey(app)
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

        try {
            windowManager?.addView(view, params)
            visible = true
            mainHandler.removeCallbacks(enableTouchesRunnable)
            mainHandler.postDelayed(enableTouchesRunnable, CLICK_GUARD_MS)
        } catch (_: Exception) {
            overlayView = null
            visible = false
        }
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
        hide(app)
    }

    private fun detach() {
        mainHandler.removeCallbacks(enableTouchesRunnable)
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
