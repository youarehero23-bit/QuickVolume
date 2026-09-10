package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * Ultra-lightweight background service that adds a transparent touch layer over the Android status bar.
 * Detects horizontal slide gestures across any application:
 * - Slide Right: Gradually increases media volume.
 * - Slide Left: Gradually decreases media volume.
 * - Swipe Down: Automatically expands the notification shade so native phone behavior is preserved.
 *
 * Highly optimized: Zero background loops, zero battery drain when idle.
 */
class StatusBarVolumeService : Service() {

    companion object {
        const val ACTION_STOP_SERVICE = "com.example.quickvolume.ACTION_STOP_SERVICE"
        const val CHANNEL_ID = "quickvolume_gesture_channel"
        const val NOTIFICATION_ID = 4001
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, StatusBarVolumeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StatusBarVolumeService::class.java)
            context.stopService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: StatusBarTouchView? = null
    private lateinit var volumeManager: VolumeManager
    private lateinit var prefs: GesturePreferences

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> overlayView?.visibility = View.GONE
                Intent.ACTION_SCREEN_ON -> overlayView?.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        volumeManager = VolumeManager(this)
        prefs = GesturePreferences(this)

        startForegroundNotification()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        setupOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            prefs.isGestureEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}

        removeOverlayView()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Status Bar Volume Gesture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status bar slide gesture listener for volume control"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            3001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StatusBarVolumeService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            3002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_volume_up)
            .setContentTitle("QuickVolume Gesture Active")
            .setContentText("Slide along the status bar to adjust volume")
            .setContentIntent(openPendingIntent)
            .addAction(0, "Turn Off", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupOverlayView() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val statusBarHeight = getStatusBarHeight(this)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        overlayView = StatusBarTouchView(this).apply {
            this.layoutParams = layoutParams
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun removeOverlayView() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            (30 * context.resources.displayMetrics.density).toInt()
        }
    }

    private fun performHapticTick() {
        if (!prefs.isHapticEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Exception) {}
    }

    private fun expandNotificationShade() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = statusBarManager.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
        } catch (_: Exception) {
            try {
                val statusBarService = getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                val method = statusBarManager.getMethod("expand")
                method.invoke(statusBarService)
            } catch (_: Exception) {}
        }
    }

    /**
     * Touch intercepting view placed over the status bar.
     */
    private inner class StatusBarTouchView(context: Context) : View(context) {

        private var startX = 0f
        private var startY = 0f
        private var initialVolume = 0
        private var isSlidingVolume = false
        private var isVerticalSwipe = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAA38BDF8.toInt()
            strokeWidth = 6f
            style = Paint.Style.FILL
        }

        private var feedbackProgress = 0f

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialVolume = volumeManager.getVolume()
                    isSlidingVolume = false
                    isVerticalSwipe = false
                    feedbackProgress = 0f
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY

                    if (!isSlidingVolume && !isVerticalSwipe) {
                        if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.15f) {
                            // User is sliding horizontally across status bar
                            isSlidingVolume = true
                        } else if (dy > touchSlop * 1.5f && dy > abs(dx) * 1.15f) {
                            // User pulled down: trigger notification shade expansion
                            isVerticalSwipe = true
                            expandNotificationShade()
                            return false
                        }
                    }

                    if (isSlidingVolume) {
                        val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                        val maxVol = volumeManager.getMaxVolume().coerceAtLeast(1)
                        val minVol = volumeManager.getMinVolume()
                        val totalSteps = (maxVol - minVol).coerceAtLeast(1)

                        // Smooth gradual scaling:
                        // With sensitivity factor (default 1.0f), traversing ~60% of the screen width covers the entire volume range
                        val pixelsPerStep = (screenWidth * 0.60f / (totalSteps * prefs.sensitivity)).coerceAtLeast(18f)
                        val stepChange = (dx / pixelsPerStep).toInt()
                        val targetVolume = (initialVolume + stepChange).coerceIn(minVol, maxVol)

                        val currentVol = volumeManager.getVolume()
                        if (targetVolume != currentVol) {
                            volumeManager.setVolume(targetVolume, showUi = true)
                            performHapticTick()
                            VolumeWidgetProvider.updateAllWidgets(applicationContext)
                        }

                        feedbackProgress = (targetVolume.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                        if (prefs.showVisualLine) {
                            invalidate()
                        }
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSlidingVolume) {
                        isSlidingVolume = false
                        feedbackProgress = 0f
                        invalidate()
                    }
                    isVerticalSwipe = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (isSlidingVolume && prefs.showVisualLine && feedbackProgress > 0f) {
                // Draw a subtle, sleek glow line at the bottom of the status bar overlay
                val lineRight = width * feedbackProgress
                val lineTop = height - 4f
                val lineBottom = height.toFloat()
                canvas.drawRect(0f, lineTop, lineRight, lineBottom, indicatorPaint)
            }
        }
    }
}
