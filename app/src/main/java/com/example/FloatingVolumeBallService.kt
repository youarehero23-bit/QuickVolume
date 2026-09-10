package com.example

import android.animation.ValueAnimator
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
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * Human-loving Floating Volume Ball Service.
 *
 * Gestures:
 * 1. Instant touch & move -> Smoothly repositions the ball anywhere on screen, snapping to edge on release.
 * 2. Touch & hold in place (~250ms) -> Blooms into an interactive vertical volume capsule:
 *    - Slide UP: Gradually increases media volume with crisp tactile ticks.
 *    - Slide DOWN: Gradually decreases media volume with crisp tactile ticks.
 *    - Release: Smoothly collapses back into the sleek compact orb.
 * 3. Quick tap -> Toggles mute or shows volume HUD.
 * 4. Auto-dimming -> Gently fades to translucent when idle to never obscure screen content.
 * 5. Battery efficiency -> Pauses completely on screen off.
 */
class FloatingVolumeBallService : Service() {

    companion object {
        const val ACTION_STOP_BALL = "com.example.quickvolume.ACTION_STOP_BALL"
        const val CHANNEL_ID = "quickvolume_floating_ball_channel"
        const val NOTIFICATION_ID = 4002
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingVolumeBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingVolumeBallService::class.java)
            context.stopService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var ballView: FloatingBallTouchView? = null
    private lateinit var volumeManager: VolumeManager
    private lateinit var prefs: GesturePreferences

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> ballView?.onScreenOff()
                Intent.ACTION_SCREEN_ON -> ballView?.onScreenOn()
                "android.media.VOLUME_CHANGED_ACTION" -> ballView?.onVolumeChangedExternally()
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
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        registerReceiver(screenReceiver, filter)

        setupFloatingBall()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_BALL) {
            prefs.isFloatingBallEnabled = false
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

        removeFloatingBall()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Volume Ball",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "QuickVolume persistent floating ball overlay"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            5001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingVolumeBallService::class.java).apply {
            action = ACTION_STOP_BALL
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            5002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_volume_up)
            .setContentTitle("QuickVolume Floating Ball Active")
            .setContentText("Hold & slide up/down to adjust volume • Drag to move")
            .setContentIntent(openPendingIntent)
            .addAction(0, "Turn Off", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupFloatingBall() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val ballSizePx = (prefs.ballSizeDp * density).toInt()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Default position: Right edge, comfortably middle-upper screen
        var initialX = prefs.ballPosX
        var initialY = prefs.ballPosY
        if (initialX < 0 || initialY < 0) {
            initialX = screenWidth - ballSizePx - (12 * density).toInt()
            initialY = (screenHeight * 0.38f).toInt()
        }

        val layoutParams = WindowManager.LayoutParams(
            ballSizePx,
            ballSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX.coerceIn(0, (screenWidth - ballSizePx).coerceAtLeast(0))
            y = initialY.coerceIn((40 * density).toInt(), (screenHeight - ballSizePx - (40 * density).toInt()).coerceAtLeast(0))
        }

        ballView = FloatingBallTouchView(this, layoutParams)

        try {
            windowManager?.addView(ballView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun removeFloatingBall() {
        ballView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
            ballView = null
        }
    }

    /**
     * Custom view that renders the human-loving floating ball & handles:
     * - Instant drag -> move position
     * - Hold for ~250ms -> expand into vertical volume capsule and slide up/down
     */
    inner class FloatingBallTouchView(
        context: Context,
        private val lp: WindowManager.LayoutParams
    ) : View(context) {

        private val mainHandler = Handler(Looper.getMainLooper())
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val density = resources.displayMetrics.density

        // Sizes
        private val defaultBallSize = (prefs.ballSizeDp * density).toInt()
        private val expandedWidth = (68 * density).toInt()
        private val expandedHeight = (190 * density).toInt()

        // Interaction States
        private var isHoldForVolumeActive = false
        private var isMovingPosition = false
        private var touchDownX = 0f
        private var touchDownY = 0f
        private var initialLpX = 0
        private var initialLpY = 0
        private var initialVolumeAtHold = 0
        private var holdVolumeStartY = 0f
        private var lastVibratedVolume = -1

        // Visual / Morph Animation Progress: 0.0f (Compact Orb) -> 1.0f (Expanded Volume Capsule)
        private var morphProgress = 0f
        private var morphAnimator: ValueAnimator? = null

        // Auto-Dimming
        private var isDimmed = false
        private val autoDimRunnable = Runnable {
            if (prefs.isIdleDimmingEnabled && !isHoldForVolumeActive && !isMovingPosition) {
                animateDim(toDim = true)
            }
        }

        // Hold Detection Runnable
        private val holdDetectionRunnable = Runnable {
            if (!isMovingPosition) {
                triggerVolumeAdjustmentMode()
            }
        }

        // Drawing Paints
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            style = Paint.Style.FILL
        }

        private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 96, 165, 250) // Soft sky cyan glow
            strokeWidth = 2.5f * density
            style = Paint.Style.STROKE
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 15f * density
        }

        private val rectF = RectF()

        init {
            scheduleAutoDim()
        }

        private fun scheduleAutoDim() {
            mainHandler.removeCallbacks(autoDimRunnable)
            if (prefs.isIdleDimmingEnabled) {
                mainHandler.postDelayed(autoDimRunnable, 3200)
            }
        }

        private fun animateDim(toDim: Boolean) {
            if (isDimmed == toDim) return
            isDimmed = toDim
            val targetAlpha = if (toDim) 0.42f else 1.0f
            animate().alpha(targetAlpha).setDuration(350).start()
        }

        fun onScreenOff() {
            mainHandler.removeCallbacks(holdDetectionRunnable)
            mainHandler.removeCallbacks(autoDimRunnable)
            visibility = GONE
        }

        fun onScreenOn() {
            visibility = VISIBLE
            alpha = 1.0f
            isDimmed = false
            scheduleAutoDim()
            invalidate()
        }

        fun onVolumeChangedExternally() {
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val rawX = event.rawX
            val rawY = event.rawY

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = rawX
                    touchDownY = rawY
                    initialLpX = lp.x
                    initialLpY = lp.y
                    isHoldForVolumeActive = false
                    isMovingPosition = false

                    // Wake up from dim immediately
                    animateDim(toDim = false)
                    mainHandler.removeCallbacks(autoDimRunnable)

                    // Schedule the hold detector (240ms threshold for lovely responsiveness)
                    mainHandler.removeCallbacks(holdDetectionRunnable)
                    mainHandler.postDelayed(holdDetectionRunnable, 240)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = rawX - touchDownX
                    val dy = rawY - touchDownY
                    val distanceMoved = abs(dx) + abs(dy)

                    if (!isHoldForVolumeActive && !isMovingPosition) {
                        // User moved before the hold timer fired -> cancel hold, start moving ball position!
                        if (distanceMoved > touchSlop) {
                            mainHandler.removeCallbacks(holdDetectionRunnable)
                            isMovingPosition = true
                        }
                    }

                    if (isMovingPosition) {
                        // Free dragging across the screen
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels

                        lp.x = (initialLpX + dx.toInt()).coerceIn(0, screenWidth - lp.width)
                        lp.y = (initialLpY + dy.toInt()).coerceIn(
                            (32 * density).toInt(),
                            screenHeight - lp.height - (32 * density).toInt()
                        )
                        windowManager?.updateViewLayout(this, lp)
                        return true
                    }

                    if (isHoldForVolumeActive) {
                        // User is in Volume Adjustment Mode!
                        // Slide UP: increases volume, Slide DOWN: decreases volume
                        val verticalSlide = holdVolumeStartY - rawY
                        val maxVol = volumeManager.getMaxVolume().coerceAtLeast(1)
                        val minVol = volumeManager.getMinVolume()
                        val totalSteps = (maxVol - minVol).coerceAtLeast(1)

                        // Smooth gradual scaling: ~130dp vertical movement covers full volume spectrum
                        val pixelsPerStep = ((130 * density) / (totalSteps * prefs.sensitivity)).coerceAtLeast(12f * density)
                        val stepChange = (verticalSlide / pixelsPerStep).toInt()
                        val targetVolume = (initialVolumeAtHold + stepChange).coerceIn(minVol, maxVol)

                        val currentVol = volumeManager.getVolume()
                        if (targetVolume != currentVol) {
                            // Suppress Android system volume UI so the custom floating orb handles the feedback cleanly
                            volumeManager.setVolume(targetVolume, showUi = false)
                            val actualVol = volumeManager.getVolume()
                            if (actualVol != lastVibratedVolume) {
                                lastVibratedVolume = actualVol
                                performHapticTick()
                            }
                            VolumeWidgetProvider.updateAllWidgets(applicationContext)
                            invalidate()
                        }
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(holdDetectionRunnable)

                    if (isHoldForVolumeActive) {
                        // Finish volume adjust mode, morph back to orb
                        collapseVolumeAdjustmentMode()
                    } else if (isMovingPosition) {
                        // Finished moving position: snap to screen edge if enabled
                        finishPositionMove()
                    } else {
                        // Quick Tap without move: visual feedback & haptic pulse on orb
                        performQuickTap()
                    }

                    scheduleAutoDim()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun triggerVolumeAdjustmentMode() {
            isHoldForVolumeActive = true
            isMovingPosition = false
            initialVolumeAtHold = volumeManager.getVolume()
            lastVibratedVolume = initialVolumeAtHold
            holdVolumeStartY = touchDownY

            // Strong satisfying tactile confirmation
            performHoldHaptic()

            // Expand view dimensions
            val screenHeight = resources.displayMetrics.heightPixels
            val screenWidth = resources.displayMetrics.widthPixels

            // Center expansion around current touch/position
            val deltaHeight = expandedHeight - defaultBallSize
            val targetY = (lp.y - (deltaHeight / 2)).coerceIn(
                (40 * density).toInt(),
                screenHeight - expandedHeight - (40 * density).toInt()
            )
            val deltaWidth = expandedWidth - defaultBallSize
            val targetX = (lp.x - (deltaWidth / 2)).coerceIn(0, screenWidth - expandedWidth)

            lp.x = targetX
            lp.y = targetY
            lp.width = expandedWidth
            lp.height = expandedHeight
            windowManager?.updateViewLayout(this, lp)

            // Animate morph
            morphAnimator?.cancel()
            morphAnimator = ValueAnimator.ofFloat(morphProgress, 1.0f).apply {
                duration = 260
                interpolator = OvershootInterpolator(1.1f)
                addUpdateListener {
                    morphProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun collapseVolumeAdjustmentMode() {
            isHoldForVolumeActive = false
            morphAnimator?.cancel()
            morphAnimator = ValueAnimator.ofFloat(morphProgress, 0.0f).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    morphProgress = it.animatedValue as Float
                    invalidate()
                    if (morphProgress == 0f) {
                        // Reset back to compact size
                        lp.width = defaultBallSize
                        lp.height = defaultBallSize
                        val screenWidth = resources.displayMetrics.widthPixels
                        if (prefs.isSnapToEdgeEnabled) {
                            snapToNearestEdge()
                        } else {
                            windowManager?.updateViewLayout(this@FloatingBallTouchView, lp)
                            savePosition()
                        }
                    }
                }
                start()
            }
        }

        private fun finishPositionMove() {
            isMovingPosition = false
            if (prefs.isSnapToEdgeEnabled) {
                snapToNearestEdge()
            } else {
                savePosition()
            }
        }

        private fun snapToNearestEdge() {
            val screenWidth = resources.displayMetrics.widthPixels
            val margin = (12 * density).toInt()
            val targetX = if (lp.x + (lp.width / 2) < screenWidth / 2) {
                margin
            } else {
                screenWidth - lp.width - margin
            }

            val startX = lp.x
            ValueAnimator.ofInt(startX, targetX).apply {
                duration = 240
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    lp.x = it.animatedValue as Int
                    windowManager?.updateViewLayout(this@FloatingBallTouchView, lp)
                }
                start()
            }

            prefs.ballPosX = targetX
            prefs.ballPosY = lp.y
        }

        private fun savePosition() {
            prefs.ballPosX = lp.x
            prefs.ballPosY = lp.y
        }

        private fun performQuickTap() {
            // Tactile feedback and subtle responsive pulse on the orb without interrupting screen with Android volume UI
            performHapticTick()
            // Quick pulse visual
            animate().scaleX(1.18f).scaleY(1.18f).setDuration(120).withEndAction {
                animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start()
            }.start()
        }

        private fun performHoldHaptic() {
            if (!prefs.isHapticEnabled) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                }
            } catch (_: Exception) {}
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
                        VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                }
            } catch (_: Exception) {}
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val maxVol = volumeManager.getMaxVolume().coerceAtLeast(1)
            val currentVol = volumeManager.getVolume()
            val isMuted = volumeManager.isMuted() || currentVol == 0
            val volumeRatio = (currentVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
            val volumePercentage = (volumeRatio * 100).toInt()

            val cornerRadius = (w / 2f)
            rectF.set(4f * density, 4f * density, w - (4f * density), h - (4f * density))

            // 1. Background Capsule / Orb with rich deep dark-glass gradient
            val baseGradient = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(Color.rgb(30, 41, 59), Color.rgb(15, 23, 42)),
                null,
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = baseGradient
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)

            // 2. Cyan/Sky Aura Border
            borderPaint.color = if (isMuted) Color.argb(180, 239, 68, 68) else Color.argb(160, 56, 189, 248)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)

            if (morphProgress > 0.1f) {
                // EXPANDED VOLUME SLIDER MODE
                val sliderMarginX = 14f * density
                val sliderTop = 44f * density
                val sliderBottom = h - (44f * density)
                val sliderHeight = sliderBottom - sliderTop

                // Track background
                val trackRect = RectF(sliderMarginX, sliderTop, w - sliderMarginX, sliderBottom)
                canvas.drawRoundRect(trackRect, 8f * density, 8f * density, trackBgPaint)

                // Track fill (Active volume from bottom to top)
                val fillTop = sliderBottom - (sliderHeight * volumeRatio)
                val fillRect = RectF(sliderMarginX, fillTop, w - sliderMarginX, sliderBottom)
                val fillGradient = LinearGradient(
                    0f, fillTop, 0f, sliderBottom,
                    if (isMuted) intArrayOf(Color.rgb(239, 68, 68), Color.rgb(185, 28, 28))
                    else intArrayOf(Color.rgb(56, 189, 248), Color.rgb(37, 99, 235)),
                    null,
                    Shader.TileMode.CLAMP
                )
                trackFillPaint.shader = fillGradient
                canvas.drawRoundRect(fillRect, 8f * density, 8f * density, trackFillPaint)

                // Top symbol '+'
                symbolPaint.textSize = 18f * density
                canvas.drawText("+", w / 2f, 26f * density, symbolPaint)

                // Bottom symbol '-' or Mute
                symbolPaint.textSize = 18f * density
                canvas.drawText(if (isMuted) "✕" else "−", w / 2f, h - (16f * density), symbolPaint)

                // Center percentage text
                textPaint.textSize = 15f * density
                textPaint.color = Color.WHITE
                val textY = (sliderTop + sliderBottom) / 2f + (5f * density)
                canvas.drawText(if (isMuted) "MUTE" else "$volumePercentage%", w / 2f, textY, textPaint)

            } else {
                // COMPACT ORB MODE
                // Draws volume percentage or speaker glyph
                textPaint.textSize = 14f * density
                textPaint.color = if (isMuted) Color.rgb(248, 113, 113) else Color.rgb(224, 242, 254)

                val displayLabel = if (isMuted) "MUTE" else "$volumePercentage%"
                val fontMetrics = textPaint.fontMetrics
                val textCenterY = (h / 2f) - ((fontMetrics.ascent + fontMetrics.descent) / 2f) - (3f * density)

                canvas.drawText(displayLabel, w / 2f, textCenterY, textPaint)

                // Mini sound wave indicator arc beneath text
                symbolPaint.textSize = 10f * density
                symbolPaint.color = Color.argb(180, 147, 197, 253)
                val subText = if (isMuted) "●" else "VOL"
                canvas.drawText(subText, w / 2f, textCenterY + (13f * density), symbolPaint)
            }
        }
    }
}
