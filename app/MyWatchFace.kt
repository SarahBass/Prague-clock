package com.virtualstarstudios.pragueclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.palette.graphics.Palette
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

private const val HOUR_STROKE_WIDTH = 9f
private const val MINUTE_STROKE_WIDTH = 7f
private const val SECOND_TICK_STROKE_WIDTH = 2f

private const val CENTER_GAP_AND_CIRCLE_RADIUS = 4f

private const val SHADOW_RADIUS = 6f
private var counter: Int = 0
private var heartRate: Float = 0f
private var stepCount: Float = 0f

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn"t
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */


class MyWatchFace : CanvasWatchFaceService(), SensorEventListener {



    override fun onCreate() {
        Log.i("tag", "onStart Service ${this.application.applicationInfo}")

        val mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL, 5_000_000);

        val mStepCounter = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        mSensorManager.registerListener(this, mStepCounter, SensorManager.SENSOR_DELAY_NORMAL, 5_000_000);

        super.onCreate()
    }

    override fun onStart(intent: Intent?, startId: Int) {
        Log.i("tag", "onStart Service $intent")

        super.onStart(intent, startId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("tag", "onStart Service $intent")

//        val CHANNEL_ID = "my_channel_01"
//        val channel = NotificationChannel(
//            CHANNEL_ID,
//            "Channel human readable title",
//            NotificationManager.IMPORTANCE_DEFAULT
//        )
//
//        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
//            channel
//        )
//
//        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
//            .setContentTitle("")
//            .setContentText("").build()
//
//        this.startForeground(1, notification)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: Engine) : Handler() {
        private val mWeakReference: WeakReference<Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }


    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F

        private var mSecondHandLength: Float = 0F
        private var sMinuteHandLength: Float = 0F
        private var sHourHandLength: Float = 0F

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private var mWatchHandColor: Int = 0
        private var mWatchHandHighlightColor: Int = 0
        private var mWatchHandShadowColor: Int = 0

        private lateinit var mHourPaint: Paint
        private lateinit var mMinutePaint: Paint
        private lateinit var mSecondPaint: Paint
        private lateinit var mTickAndCirclePaint: Paint

        private lateinit var mForegroundBitmap: Bitmap

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mGrayBackgroundBitmap: Bitmap

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            initializeBackground()
            initializeWatchFace()
        }



        private fun getMoonPhase(): String {
            val d = Date()
            val sdf1 = SimpleDateFormat("d")
            val dayOfMonth: String = sdf1.format(d)
            val LUNAR_MONTH = 29.530588853;
            val newMoondifference = abs((Integer.parseInt(dayOfMonth)) - (Integer.parseInt(getnewMoonDate())))
            val fullMoondifference = abs((Integer.parseInt(dayOfMonth)) - (Integer.parseInt(getFullMoonDate())))
            val moonPercent : Double = newMoondifference / LUNAR_MONTH

            val moonString : String =
                if (fullMoondifference == 0){"Full Moon"}
            else if (newMoondifference == 0 ){"New Moon"}
                else if (Integer.parseInt(dayOfMonth) > (Integer.parseInt(getFullMoonDate()))) {"Waning " +
                        if ((moonPercent >= .05 && moonPercent < 0.25) || (moonPercent >= 0.75 && moonPercent < 0.95)){ "Crescent Moon" }
                        else if ((moonPercent >= 0.25 && moonPercent < 0.35)||(moonPercent >= 0.65 && moonPercent < 0.75) ){ "Half Moon"}
                else {"Gibbous Moon"}}
                else if (Integer.parseInt(dayOfMonth) < (Integer.parseInt(getFullMoonDate()))) {"Waxing " +
                        if ((moonPercent >= .05 && moonPercent < 0.25) || (moonPercent >= 0.75 && moonPercent < 0.95)){ "Crescent Moon" }
                        else if ((moonPercent >= 0.25 && moonPercent < 0.35)||(moonPercent >= 0.65 && moonPercent < 0.75) ){ "Half Moon"}
                        else {"Gibbous Moon"}}
                else{"Plain Moon"}

            return moonString
        }

        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap = getAstrologyBackground()
/* Extracts colors from background image to improve watchface style. */
            Palette.from(mBackgroundBitmap).generate {
                it?.let {
                    mWatchHandHighlightColor = it.getVibrantColor(Color.WHITE)
                    mWatchHandColor = it.getLightVibrantColor(Color.WHITE)
                    mWatchHandShadowColor = it.getDarkMutedColor(Color.BLACK)
                    updateWatchHandStyle()
                }
            }
        }

    private fun changeWandColor() {
        /* Extracts colors from background image to improve watchface style. */
        Palette.from(mBackgroundBitmap).generate {
            it?.let {
                mWatchHandHighlightColor = it.getVibrantColor(Color.WHITE)
                mWatchHandColor = it.getLightVibrantColor(Color.WHITE)
                mWatchHandShadowColor = it.getDarkMutedColor(Color.BLACK)
                updateWatchHandStyle()
            }
        }}

        private fun initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE
            mWatchHandHighlightColor = Color.WHITE
            mWatchHandShadowColor = Color.BLACK

            mHourPaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = HOUR_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }

            mMinutePaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = MINUTE_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }

            mSecondPaint = Paint().apply {
                color = mWatchHandHighlightColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }

            mTickAndCirclePaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                style = Paint.Style.STROKE
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()

            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        private fun updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.color = Color.WHITE
                mMinutePaint.color = Color.WHITE
                mSecondPaint.color = Color.WHITE
                mTickAndCirclePaint.color = Color.WHITE

                mHourPaint.isAntiAlias = false
                mMinutePaint.isAntiAlias = false
                mSecondPaint.isAntiAlias = false
                mTickAndCirclePaint.isAntiAlias = false

                mHourPaint.clearShadowLayer()
                mMinutePaint.clearShadowLayer()
                mSecondPaint.clearShadowLayer()
                mTickAndCirclePaint.clearShadowLayer()

            } else {
                mHourPaint.color = mWatchHandColor
                mMinutePaint.color = mWatchHandColor
                mSecondPaint.color = mWatchHandHighlightColor
                mTickAndCirclePaint.color = mWatchHandColor

                mHourPaint.isAntiAlias = true
                mMinutePaint.isAntiAlias = true
                mSecondPaint.isAntiAlias = true
                mTickAndCirclePaint.isAntiAlias = true

                mHourPaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
                mMinutePaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
                mSecondPaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
                mTickAndCirclePaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
                )
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mHourPaint.alpha = if (inMuteMode) 100 else 255
                mMinutePaint.alpha = if (inMuteMode) 100 else 255
                mSecondPaint.alpha = if (inMuteMode) 80 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (mCenterX * 0.6).toFloat()
            sMinuteHandLength = (mCenterX * 0.6).toFloat()
            sHourHandLength = (mCenterX * 0.5).toFloat()

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / mBackgroundBitmap.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(
                mBackgroundBitmap,
                (mBackgroundBitmap.width * scale).toInt(),
                (mBackgroundBitmap.height * scale).toInt(), true
            )

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don"t want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren"t
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                mBackgroundBitmap.width,
                mBackgroundBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(mGrayBackgroundBitmap)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, grayPaint)
        }
        private fun drawDay(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("EEE")
            val d = Date()
            val dayOfTheWeek: String = sdf.format(d)
            var drawable : Int = when (dayOfTheWeek){
                "Mon" ->R.drawable.monh
                "Tue" ->R.drawable.tueh
                "Wed" ->R.drawable.wedh
                "Thu" ->R.drawable.thuh
                "Fri" ->R.drawable.frih
                "Sat" ->R.drawable.sath
                "Sun" ->R.drawable.sunh

                else -> R.drawable.sunh}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }
        private fun drawStepsFace(canvas: Canvas) {
            if(mAmbient){
                val steps: Int = (stepCount / 100).roundToInt()
                val innerTickRadius = mCenterX - 44
                val outerTickRadius = mCenterX - 54
                for (tickIndex in 0..steps) {
                    val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 105).toFloat()
                    val innerX = Math.sin(tickRot.toDouble()).toFloat() * innerTickRadius
                    val innerY = (-Math.cos(tickRot.toDouble())).toFloat() * innerTickRadius
                    val outerX = Math.sin(tickRot.toDouble()).toFloat() * outerTickRadius
                    val outerY = (-Math.cos(tickRot.toDouble())).toFloat() * outerTickRadius
                    canvas.drawLine(
                        mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint
                    )
                }}else{}
        }

        private fun draw24HourClock(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("m")
            val d = Date()
            val minutes: String = sdf.format(d)

            var drawable : Int = when (Integer.parseInt(minutes)%10){
                1->R.drawable._4hour1
                2 ->R.drawable._4hour2
                3 ->R.drawable._4hour3
                4 ->R.drawable._4hour4
                5->R.drawable._4hour5
                6 ->R.drawable._4hour6
                7 ->R.drawable._4hour7
                8 ->R.drawable._4hour8
                9 ->R.drawable._4hour9
                0 ->R.drawable._4hour0
                else -> R.drawable._4hour0}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left , bounds.top, bounds.right , bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun draw24HourClockTens(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("m")
            val d = Date()
            val minutes: String = sdf.format(d)

            var drawable : Int = when (Integer.parseInt(minutes)/10){
                1->R.drawable._4hour1
                2 ->R.drawable._4hour2
                3 ->R.drawable._4hour3
                4 ->R.drawable._4hour4
                5->R.drawable._4hour5
                6 ->R.drawable._4hour6
                7 ->R.drawable._4hour7
                8 ->R.drawable._4hour8
                9 ->R.drawable._4hour9
                0 ->R.drawable._4hour0
                else -> R.drawable._4hour0}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left , bounds.top, bounds.right -23, bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun draw24HourClockHundreds(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("h")
            val d = Date()
            val hours: String = sdf.format(d)

            var drawable : Int = when (Integer.parseInt(hours)%10){
                1->R.drawable._4hour1
                2 ->R.drawable._4hour2
                3 ->R.drawable._4hour3
                4 ->R.drawable._4hour4
                5->R.drawable._4hour5
                6 ->R.drawable._4hour6
                7 ->R.drawable._4hour7
                8 ->R.drawable._4hour8
                9 ->R.drawable._4hour9
                0 ->R.drawable._4hour0
                else -> R.drawable._4hour0}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left , bounds.top, bounds.right-56, bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun draw24HourClockThousands(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("h")
            val d = Date()
            val hours: String = sdf.format(d)

            var drawable : Int = when (Integer.parseInt(hours)/10){
                1->R.drawable._4hour1
                2 ->R.drawable._4hour2
                0 ->R.drawable.blank
                else -> R.drawable.blank}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left , bounds.top, bounds.right-80, bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun AmericanClockHours(canvas: Canvas, bounds: Rect) {
            val d = Date()
            val sdf1 = SimpleDateFormat("m")

            val minutes: String = sdf1.format(d)
            val sdf = SimpleDateFormat("h")
            val hours: String = sdf.format(d)

            var drawable : Int =
                if (Integer.parseInt(minutes) >29){
                    when(hours){
                    "12"->R.drawable.ahalf12
                    "1" ->R.drawable.ahalf1
                    "2" ->R.drawable.ahalf2
                    "3" ->R.drawable.ahalf3
                    "4"->R.drawable.ahalf4
                    "5" ->R.drawable.ahalf5
                    "6" ->R.drawable.ahalf6
                    "7" ->R.drawable.ahalf7
                    "8" ->R.drawable.ahalf8
                    "9" ->R.drawable.ahalf9
                    "10" ->R.drawable.ahalf10
                    "11" ->R.drawable.ahalf11
                        else -> R.drawable.ahalf12}
                }else{
                    when (hours){
                "12"->R.drawable.americanhour12
                "1" ->R.drawable.americanhour1
                "2" ->R.drawable.americanhour2
                "3" ->R.drawable.americanhour3
                "4"->R.drawable.americanhour4
                "5" ->R.drawable.americanhour5
                "6" ->R.drawable.americanhour6
                "7" ->R.drawable.americanhour7
                "8" ->R.drawable.americanhour8
                "9" ->R.drawable.americanhour9
                "10" ->R.drawable.americanhour10
                "11" ->R.drawable.americanhour11
                        else -> R.drawable.americanhour12}}


            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left , bounds.top, bounds.right, bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun AmericanClockMinutes(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("m")

            val d = Date()
            val minutes: String = sdf.format(d)


            var drawable : Int =
                if (Integer.parseInt(minutes)%5 > 2){
                    when ((floor((Integer.parseInt(minutes) / 5).toDouble()).toInt())) {
                    12->R.drawable.ahalf12
                    1 ->R.drawable.ahalf1
                    2 ->R.drawable.ahalf2
                    3 ->R.drawable.ahalf3
                    4->R.drawable.ahalf4
                    5 ->R.drawable.ahalf5
                    6 ->R.drawable.ahalf6
                    7 ->R.drawable.ahalf7
                    8 ->R.drawable.ahalf8
                    9 ->R.drawable.ahalf9
                    10 ->R.drawable.ahalf10
                    11 ->R.drawable.ahalf11
                    else -> R.drawable.ahalf12}
                }else {
                    when ((floor((Integer.parseInt(minutes) / 5).toDouble()).toInt())) {
                        12 -> R.drawable.americanhour12
                        1 -> R.drawable.americanhour1
                        2 -> R.drawable.americanhour2
                        3 -> R.drawable.americanhour3
                        4 -> R.drawable.americanhour4
                        5 -> R.drawable.americanhour5
                        6 -> R.drawable.americanhour6
                        7 -> R.drawable.americanhour7
                        8 -> R.drawable.americanhour8
                        9 -> R.drawable.americanhour9
                        10 -> R.drawable.americanhour10
                        11 -> R.drawable.americanhour11
                        else -> R.drawable.americanhour12
                    }
                }
            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left , bounds.top , bounds.right , bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }


        private fun drawAMPM(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("a")
            val d = Date()
            val amPM: String = sdf.format(d)

            var drawable : Int = when (amPM){
                "AM" ->R.drawable.amh
                "PM"->R.drawable.pmh
                else -> R.drawable.pmh}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left  , bounds.top, bounds.right   , bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun drawAstrology(canvas: Canvas, bounds: Rect) {

            var drawable : Int = when (getHoroscope()){
                "Aries" -> R.drawable.astroaries
                "Taurus" -> R.drawable.astrotaurus
                "Gemini" -> R.drawable.astrogemini
                "Cancer" -> R.drawable.astrocancer
                "Leo" -> R.drawable.astroleo
                "Virgo" -> R.drawable.astrowoman
                "Libra" -> R.drawable.astrolibra
                "Scorpio" ->R.drawable.astroscorpion
                "Sagittarius" -> R.drawable.astrosagit
                "Capricorn" -> R.drawable.astrocapricorn
                "Aquarius" -> R.drawable.astrocups
                "Pisces" -> R.drawable.astropices
                else -> R.drawable.astropices}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left  , bounds.top, bounds.right   , bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun drawMoon(canvas: Canvas, bounds: Rect) {

            var drawable : Int = when (getMoonPhase()){
                    "New Moon" -> R.drawable.moonh0
                    "Waxing Crescent Moon"-> R.drawable.moonh1
                    "Waxing Half Moon"-> R.drawable.moonh2
                    "Waxing Gibbous Moon"-> R.drawable.moonh3
                    "Full Moon" -> R.drawable.moonh4
                    "Waning Gibbous Moon"-> R.drawable.moonh5
                    "Waning Half Moon"-> R.drawable.moonh6
                    "Waning Crescent Moon"-> R.drawable.moonh7
                else -> R.drawable.moonh3}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left  , bounds.top, bounds.right   , bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }



        private fun drawSuns(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("D")
            val d = Date()
            val daysinyear: String = sdf.format(d)
            var drawable : Int = when (Integer.parseInt(daysinyear)){
                79 -> R.drawable.sprinequi //"Spring Equinox March 20th"
                172 -> R.drawable.summersol//"Summer Solstice June 21st"
                265 -> R.drawable.fallequi//"Fall Equinox: September 22nd"
                355 -> R.drawable.wintersol//"Winter Solstice : December 21st"
                else -> R.drawable.blank}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left  , bounds.top, bounds.right   , bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now
            updateWatchHandStyle()
            drawBackground(canvas)
            drawPWeek(canvas, bounds)
            drawPdate(canvas, bounds)
            drawPrague(canvas, bounds)
            drawPHHANDS(canvas, bounds)
            drawPointer(canvas, bounds)
            draw12fix(canvas, bounds)
            drawPointerMonth(canvas, bounds)
            drawWatchFace(canvas)
            drawStepsFace(canvas)
            drawDates(canvas, bounds)
            drawDatesTen(canvas, bounds)
            drawMonths(canvas, bounds)
            drawMonthsTen(canvas, bounds)
            drawDay(canvas, bounds)
            drawMoon(canvas, bounds)
            drawAstrology(canvas, bounds)
            drawSuns(canvas, bounds)
            draw24HourClock(canvas, bounds)
            draw24HourClockTens(canvas, bounds)
            draw24HourClockHundreds(canvas, bounds)
            draw24HourClockThousands(canvas, bounds)
            AmericanClockHours(canvas, bounds)
            AmericanClockMinutes(canvas, bounds)
            drawAMPM(canvas, bounds)
            drawHeartRates(canvas, bounds)
            drawHeartRatesTens(canvas, bounds)
            drawHeartRatesHundreds(canvas, bounds)
            initGrayBackgroundBitmap()
            changeWandColor()
        }

        private fun drawBackground(canvas: Canvas) {
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            } else {
                mBackgroundBitmap = Bitmap.createScaledBitmap(
                    getAstrologyBackground(),
                    mBackgroundBitmap.width,
                    mBackgroundBitmap.height, true
                )

                canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            }
        }

        private fun drawWatchFace(canvas: Canvas) {

            val seconds =
                mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f
            val secondsRotation = seconds * 6f
            val minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f
            val hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar.get(Calendar.HOUR) * 30 + hourHandOffset

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save()

            canvas.rotate(hoursRotation, mCenterX, mCenterY)

            if (!mAmbient) {
                canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourPaint
                )
            }else{}

            if (!mAmbient) {
                canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY)
                canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sMinuteHandLength,
                    mMinutePaint
                )
            }
            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY)
                canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - mSecondHandLength,
                    mSecondPaint
                )

            }
            canvas.drawCircle(
                mCenterX,
                mCenterY,
                CENTER_GAP_AND_CIRCLE_RADIUS,
                mTickAndCirclePaint
            )

            /* Restore the canvas" original orientation. */
            canvas.restore()
        }



        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren"t visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */

        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {

            //Shows different methods to call strings
            when (tapType) {
                TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                TAP_TYPE_TAP -> { counter +=1
                    getToast()}


                    else -> {
                        counter += 0}}
                    if (counter > 8){
                        counter = 0 }


            invalidate()
        }

        private fun getToast() {
            val frameTime = INTERACTIVE_UPDATE_RATE_MS
            val sdf = SimpleDateFormat("EEE")
            val sdf1 = SimpleDateFormat("EEEE")
            val sdf2 = SimpleDateFormat("MMMM")
            val sdf3 = SimpleDateFormat("d")
            val sdf4 = SimpleDateFormat("yyyy")
            val sdf5 = SimpleDateFormat("MMMM d yyyy")
            val sdf6 = SimpleDateFormat("h:mm:ss a")
            val sdf7 = SimpleDateFormat("a")
            val d = Date()
            val dayOfTheWeek: String = sdf.format(d)
            val dayOfTheWeekLong: String = sdf1.format(d)
            val monthOfYear: String = sdf2.format(d)
            val dayOfMonth: String = sdf3.format(d)

            val year4digits: String = sdf4.format(d)
            val fullDateSpaces: String = sdf5.format(d)
            val timeSpecific : String = sdf6.format(d)
            val amPM : String = sdf7.format(d)
            when (counter) {


                0 -> when (getHoroscope()) {
                    "Aquarius" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope0,
                        Toast.LENGTH_SHORT
                    )
                    "Aries" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope1,
                        Toast.LENGTH_SHORT
                    )
                    "Cancer" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope2,
                        Toast.LENGTH_SHORT
                    )
                    "Capricorn" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope3,
                        Toast.LENGTH_SHORT
                    )
                    "Gemini" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope4,
                        Toast.LENGTH_SHORT
                    )
                    "Leo" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope5,
                        Toast.LENGTH_SHORT
                    )
                    "Libra" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope6,
                        Toast.LENGTH_SHORT
                    )
                    "Pisces" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope7,
                        Toast.LENGTH_SHORT
                    )
                    "Sagittarius" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope8,
                        Toast.LENGTH_SHORT
                    )
                    "Scorpio" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope9,
                        Toast.LENGTH_SHORT
                    )
                    "Taurus" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope10,
                        Toast.LENGTH_SHORT
                    )
                    "Virgo" -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope11,
                        Toast.LENGTH_SHORT
                    )
                    else -> Toast.makeText(
                        applicationContext,
                        R.string.horoscope2,
                        Toast.LENGTH_SHORT
                    )
                }
                1 -> Toast.makeText(applicationContext, getAnimationCase(), Toast.LENGTH_SHORT)
                2 -> Toast.makeText(applicationContext, timeSpecific, Toast.LENGTH_SHORT)
                3 -> Toast.makeText(applicationContext, getMoonPhase(), Toast.LENGTH_SHORT)
                4 -> if (getPlanetEventTYPE(filterPlanetNews()) == "none") {
                    Toast.makeText(
                        applicationContext,
                        "$dayOfTheWeek , $fullDateSpaces",
                        Toast.LENGTH_SHORT
                    )
                } else {
                    Toast.makeText(applicationContext, getPlanetEvent(), Toast.LENGTH_SHORT)
                }
                5 -> if (getPlanetEventTYPE(filterPlanetNews2()) == "none") {
                    Toast.makeText(
                        applicationContext,
                        "$dayOfTheWeek , $fullDateSpaces",
                        Toast.LENGTH_SHORT
                    )
                } else {
                    Toast.makeText(applicationContext, getPlanetEvent2(), Toast.LENGTH_SHORT)
                }
                6 -> Toast.makeText(applicationContext, getPlanetEvent3(), Toast.LENGTH_SHORT)
                7 -> Toast.makeText(
                    applicationContext,
                    getPlanetEvent1() + ": " + monthOfYear + " " + getFullMoonDate(),
                    Toast.LENGTH_SHORT
                )
                8 -> Toast.makeText(
                    applicationContext,
                    "$dayOfTheWeek , $fullDateSpaces", Toast.LENGTH_SHORT
                )
                else ->
                    when (getHoroscope()) {
                        "Aquarius" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope0,
                            Toast.LENGTH_SHORT
                        )
                        "Aries" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope1,
                            Toast.LENGTH_SHORT
                        )
                        "Cancer" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope2,
                            Toast.LENGTH_SHORT
                        )
                        "Capricorn" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope3,
                            Toast.LENGTH_SHORT
                        )
                        "Gemini" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope4,
                            Toast.LENGTH_SHORT
                        )
                        "Leo" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope5,
                            Toast.LENGTH_SHORT
                        )
                        "Libra" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope6,
                            Toast.LENGTH_SHORT
                        )
                        "Pisces" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope7,
                            Toast.LENGTH_SHORT
                        )
                        "Sagittarius" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope8,
                            Toast.LENGTH_SHORT
                        )
                        "Scorpio" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope9,
                            Toast.LENGTH_SHORT
                        )
                        "Taurus" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope10,
                            Toast.LENGTH_SHORT
                        )
                        "Virgo" -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope11,
                            Toast.LENGTH_SHORT
                        )
                        else -> Toast.makeText(
                            applicationContext,
                            R.string.horoscope2,
                            Toast.LENGTH_SHORT
                        )
                    }
            }


                .show()
        }

        private fun getFullMoonDate(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("yyyy MMMM")
            val yearMonth: String = sdf0.format(d)
            val fullMoonDate = when(yearMonth){
                "2022 January" -> "17"
                "2022 February" -> "16"
                "2022 March" -> "18"
                "2022 April" -> "16"
                "2022 May" -> "16"
                "2022 June" -> "14"
                "2022 July" -> "13"
                "2022 August" -> "11"
                "2022 September" -> "10"
                "2022 October" -> "9"
                "2022 November" -> "8"
                "2022 December" -> "7"
                "2023 January" -> "6"
                "2023 February" -> "5"
                "2023 March" -> "7"
                "2023 April" -> "5"
                "2023 May" -> "5"
                "2023 June" -> "3"
                "2023 July" -> "3"
                "2023 August" -> "1"
                "2023 September" -> "29"
                "2023 October" -> "28"
                "2023 November" -> "27"
                "2023 December" -> "26"
                "2024 January" -> "25"
                "2024 February" -> "24"
                "2024 March" -> "25"
                "2024 April" -> "23"
                "2024 May" -> "23"
                "2024 June" -> "21"
                "2024 July" -> "21"
                "2024 August" -> "19"
                "2024 September" -> "17"
                "2024 October" -> "17"
                "2024 November" -> "15"
                "2024 December" -> "15"
                "2025 January" -> "13"
                "2025 February" -> "12"
                "2025 March" -> "13"
                "2025 April" -> "12"
                "2025 May" -> "12"
                "2025 June" -> "11"
                "2025 July" -> "10"
                "2025 August" -> "9"
                "2025 September" -> "7"
                "2025 October" -> "6"
                "2025 November" -> "5"
                "2025 December" -> "4"
                "2026 January" -> "3"
                "2026 February" -> "1"
                "2026 March" -> "3"
                "2026 April" -> "1"
                "2026 May" -> "1"
                "2026 June" -> "29"
                "2026 July" -> "29"
                "2026 August" -> "27"
                "2026 September" -> "26"
                "2026 October" -> "25"
                "2026 November" -> "24"
                "2026 December" -> "23"
                "2027 January" -> "22"
                "2027 February" -> "20"
                "2027 March" -> "22"
                "2027 April" -> "20"
                "2027 May" -> "20"
                "2027 June" -> "18"
                "2027 July" -> "18"
                "2027 August" -> "17"
                "2027 September" -> "15"
                "2027 October" -> "15"
                "2027 November" -> "13"
                "2027 December" -> "13"
                "2028 January" -> "11"
                "2028 February" -> "10"
                "2028 March" -> "10"
                "2028 April" -> "9"
                "2028 May" -> "8"
                "2028 June" -> "6"
                "2028 July" -> "6"
                "2028 August" -> "5"
                "2028 September" -> "3"
                "2028 October" -> "3"
                "2028 November" -> "2"
                "2028 December" -> "1"
                "2029 January" -> "29"
                "2029 February" -> "28"
                "2029 March" -> "29"
                "2029 April" -> "28"
                "2029 May" -> "27"
                "2029 June" -> "25"
                "2029 July" -> "25"
                "2029 August" -> "23"
                "2029 September" -> "22"
                "2029 October" -> "22"
                "2029 November" -> "20"
                "2029 December" -> "20"
                "2030 January" -> "19"
                "2030 February" -> "17"
                "2030 March" -> "19"
                "2030 April" -> "17"
                "2030 May" -> "15"
                "2030 June" -> "14"
                "2030 July" -> "13"
                "2030 August" -> "11"
                "2030 September" -> "11"
                "2030 October" -> "11"
                "2030 November" -> "9"
                "2030 December" -> "9"
                else -> "1"
            }
            return fullMoonDate
        }

        private fun getnewMoonDate(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("yyyy MMMM")
            val yearMonth: String = sdf0.format(d)
            val newMoonDate = when(yearMonth){
                "2022 January" -> 2
                "2022 February" -> 1 //It's Actually Jan31
                "2022 March" -> 2 //March31
                "2022 April" -> 30
                "2022 May" -> 30
                "2022 June" -> 28
                "2022 July" -> 28
                "2022 August" -> 27
                "2022 September" -> 25
                "2022 October" -> 25
                "2022 November" -> 23
                "2022 December" -> 23

                "2023 January" -> 21
                "2023 February" -> 19
                "2023 March" -> 21
                "2023 April" -> 19
                "2023 May" -> 19
                "2023 June" -> 17
                "2023 July" -> 17
                "2023 August" -> 16
                "2023 September" -> 14
                "2023 October" -> 14
                "2023 November" -> 13
                "2023 December" -> 12

                "2024 January" -> 11
                "2024 February" -> 9
                "2024 March" -> 10
                "2024 April" -> 8
                "2024 May" -> 7
                "2024 June" -> 6
                "2024 July" -> 5
                "2024 August" -> 4
                "2024 September" -> 2
                "2024 October" -> 2
                "2024 November" -> 1 //Nov 30
                "2024 December" -> 30

                "2025 January" -> 29
                "2025 February" -> 27
                "2025 March" -> 29
                "2025 April" -> 27
                "2025 May" -> 26
                "2025 June" -> 25
                "2025 July" -> 24
                "2025 August" -> 22
                "2025 September" -> 21
                "2025 October" -> 21
                "2025 November" -> 19
                "2025 December" -> 19

                "2026 January" -> 18
                "2026 February" -> 17
                "2026 March" -> 18
                "2026 April" -> 17
                "2026 May" -> 16
                "2026 June" -> 14
                "2026 July" -> 14
                "2026 August" -> 12
                "2026 September" -> 10
                "2026 October" -> 10
                "2026 November" -> 8
                "2026 December" -> 8

                "2027 January" -> 7
                "2027 February" -> 6
                "2027 March" -> 8
                "2027 April" -> 6
                "2027 May" -> 6
                "2027 June" -> 4
                "2027 July" -> 3
                "2027 August" -> 2
                "2027 September" -> 29
                "2027 October" -> 29
                "2027 November" -> 27
                "2027 December" -> 27

                "2028 January" -> 26
                "2028 February" -> 25
                "2028 March" -> 25
                "2028 April" -> 24
                "2028 May" -> 24
                "2028 June" -> 22
                "2028 July" -> 21
                "2028 August" -> 20
                "2028 September" -> 18
                "2028 October" -> 17
                "2028 November" -> 16
                "2028 December" -> 15

                "2029 January" -> 14
                "2029 February" -> 13
                "2029 March" -> 14
                "2029 April" -> 13
                "2029 May" -> 13
                "2029 June" -> 11
                "2029 July" -> 11
                "2029 August" -> 9
                "2029 September" -> 8
                "2029 October" -> 7
                "2029 November" -> 5
                "2029 December" -> 5

                "2030 January" -> 3
                "2030 February" -> 2
                "2030 March" -> 3
                "2030 April" -> 2
                "2030 May" -> 2
                "2030 June" -> 30
                "2030 July" -> 30
                "2030 August" -> 28
                "2030 September" -> 27
                "2030 October" -> 26
                "2030 November" -> 24
                "2030 December" -> 24
                else -> 31
            }
            return newMoonDate.toString()
        }

        private fun getPlanetEventNumber(): Int {
            var result =getPlanetEvent().filter { it.isDigit() }
            return Integer.parseInt(result)
        }

        private fun getPlanetEventNumber2(): Int {
            var result =getPlanetEvent2().filter { it.isDigit() }
            return Integer.parseInt(result)
        }



        private fun filterPlanetNews(): String {
            val d = Date()
            val sdf3 = SimpleDateFormat("d")
            val dayOfMonth: String = sdf3.format(d)
            val filteredString= if (getPlanetEventNumber() >= Integer.parseInt(dayOfMonth)) {
                    getPlanetEvent()
                }else {"none"}

            return filteredString
        }

        private fun filterPlanetNews2(): String {
            val d = Date()
            val sdf3 = SimpleDateFormat("d")
            val dayOfMonth: String = sdf3.format(d)
            val filteredString= if (getPlanetEventNumber2() >= Integer.parseInt(dayOfMonth)) {
                getPlanetEvent2()
            }else {"none"}

            return filteredString
        }




        private fun getPlanetEvent(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("yyyy MMMM")
            val sdf2 = SimpleDateFormat("MMMM")
            val sdf4 = SimpleDateFormat("yyyy")
            val monthOfYear: String = sdf2.format(d)
            val year4digits: String = sdf4.format(d)
            val yearMonth: String = sdf0.format(d)

            val planetOpposition =
                if (Integer.parseInt(year4digits) in 2026..2030){
                    when(monthOfYear){
                        "January" -> "January 4 - Quadrantids Meteor Shower"
                        "August" -> "August 13 - Perseids Meteor Shower"
                        "May" -> "May 7 - Eta Aquarids Meteor Shower"
                        "December" -> "December 14 - Geminids Meteor Shower"
                        else -> "none 0"}
                } else {
                    when (yearMonth) {
                        "2022 January" -> "Jupiter in Pisces until January 30th" //Mercury Visible at Sunset
                        "2022 February" -> "Venus Brightest on February 9" //Pluto returns. This happens only once 248 years
                        "2022 March" -> "none 0"
                        "2022 April" -> "Mercury will be visible at Sunrise until 30th"
                        "2022 May" -> "none 0"
                        "2022 June" -> "June 28: Neptune begins retrograde motion" //Mercury Visible at Sunrise
                        "2022 July" -> "July 28: Jupiter begins retrograde motion" // Pluto at Opposition 20 Jul 2022
                        "2022 August" -> "Saturn in Opposition on August 14" //August 24, 2022: Uranus begins retrograde motion
                        "2022 September" -> "Jupiter at opposition on September 26" //Septemper 16, 2022: Neptune at opposition
                        "2022 October" -> "Saturn ends retrograde motion on October 11" //Mars in Retrograde October 30, 2022
                        "2022 November" -> "November 18: Leonid Meteor Shower" // 2022 Uranus opposition – November 9
                        "2022 December" -> "December 15 - Geminids Meteor Shower" //Dec 21: December Solstice

                        "2023 January" -> "Uranus ends retrograde motion January 22nd" //Jan 7, 2023: Inferior conjunction Mercury
                        "2023 February" -> "February 16: Saturn in conjunction with the sun"
                        "2023 March" -> "Neptune at solar conjunction March 15th" //Uranus at solar conjunction
                        "2023 April" -> "April 11: Jupiter at solar conjunction"
                        "2023 May" -> "Venus Brightest on May 12" // Mercury Visible at Sunrise
                        "2023 June" -> "none 0"
                        "2023 July" -> "Pluto at Opposition : July 22 "
                        "2023 August" -> "Saturn at Opposition on August 27" // Uranus begins retrograde motion
                        "2023 September" -> "September 19 : Neptune at opposition"
                        "2023 October" -> "October 22 - Orionids Meteor Shower"
                        "2023 November" -> "Jupiter opposition – November 2" // 2023 Uranus opposition – November 13
                        "2023 December" -> "December 14 - Geminids Meteor Shower"

                        "2024 January" -> "none 0"
                        "2024 February" -> "none 0"
                        "2024 March" -> "Mercury Visible at Sunset unil 30th"
                        "2024 April" -> "none 0"
                        "2024 May" -> "Mercury Visible at Sunrise until 30th"
                        "2024 June" -> "Venus at superior solar conjunction : June 04 "
                        "2024 July" -> "Pluto at Opposition : July 23 " //Mercury visible at Sunset
                        "2024 August" -> "August 12th: Perseid Meteor Shower"
                        "2024 September" -> "Saturn is in Opposition on September 8" //Mercury visible at Sunrise
                        "2024 October" -> "none 0"
                        "2024 November" -> "Uranus opposition – November 16" //Mercury visible at sunset
                        "2024 December" -> "December 14 - Geminids Meteor Shower"

                        "2025 January" -> "Jupiter opposition– January 10" // "Mars is in Opposition on January 16th"
                        "2025 February" -> "Venus at greatest brightness: Feb 16"
                        "2025 March" -> "Mercury visible at Sunset until 30th"
                        "2025 April" -> "none 0"
                        "2025 May" -> "none 0"
                        "2025 June" -> "none 0"
                        "2025 July" -> "Pluto at Opposition : July 25"
                        "2025 August" -> "none 0"
                        "2025 September" -> "Saturn is in Opposition on September 25"
                        "2025 October" -> "none 0"
                        "2025 November" -> "Uranus opposition – November 21"
                        "2025 December" -> "December 14 - Geminids Meteor Shower"
                        else -> "none 0"
                    }
                }



            return planetOpposition

        }
        private fun getPlanetEvent1(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("MMMM")
            val Month: String = sdf0.format(d)
            val planetOpposition =
                when(Month){
                    "January" -> "Wolf Moon"
                    "February" -> "Snow Moon"
                    "March" -> "Worm Moon"
                    "April" -> "Pink Moon"
                    "May" -> "Flower Moon"
                    "June" -> "Strawberry Moon"
                    "July" -> "Buck Moon"
                    "August" -> "Sturgeon Moon"
                    "September" -> "Corn Moon"
                    "October" -> "Harvest Moon"
                    "November" -> "Beaver Moon"
                    "December" -> "Cold Moon"
                    else -> "none 0"
                }
            return planetOpposition}

        private fun getPlanetEvent2(): String {
            val d = Date()
            val sdf0 = SimpleDateFormat("yyyy MMMM")
            val yearMonth: String = sdf0.format(d)
            val planetOpposition =
                when(yearMonth){
                    "2022 January" -> "Mercury Visible at Sunset until 30th"
                    "2022 February" -> "none 0"
                    "2022 March" -> "Spring Equinox on March 20th"
                    "2022 April" -> "none 0"
                    "2022 May" -> "May 6: Eta Aquarid Meteors"
                    "2022 June" -> "June 20: Summer Solstice"
                    "2022 July" -> "Pluto at Opposition Jul 20"
                    "2022 August" -> "August 24 : Uranus begins retrograde motion"
                    "2022 September" -> "Fall Equinox September 22nd"
                    "2022 October" -> "Mars in Retrograde October 30"
                    "2022 November" -> "Uranus at opposition – November 9"
                    "2022 December" -> "Dec 21: Winter Solstice"

                    "2023 January" -> "Jan 7: Inferior conjunction Mercury"
                    "2023 February" -> "none 0"
                    "2023 March" -> "Spring Equinox on March 20th"
                    "2023 April" -> "none 0"
                    "2023 May" -> "Mercury Visible at Sunrise until 30th"
                    "2023 June" -> "June 21: Summer Solstice"
                    "2023 July" -> "none 0"
                    "2023 August" -> "August 13th: Perseid Meteor Shower"
                    "2023 September" -> "Fall Equinox September 23rd"
                    "2023 October" -> "none 0"
                    "2023 November" -> "Uranus at opposition – November 13"
                    "2023 December" -> "Winter Solstice on Dec 21st"

                    "2024 January" -> "January 4 - Quadrantids Meteor Shower"
                    "2024 February" -> "none 0"
                    "2024 March" -> "Spring Equinox March 19th"
                    "2024 April" -> "April 8: Total Solar Eclipse parts of USA"
                    "2024 May" -> "May 7 - Eta Aquarids Meteor Shower"
                    "2024 June" -> "Summer Solstice Jun 20th"
                    "2024 July" -> "Mercury visible at Sunset until 30th"
                    "2024 August" -> "August 13 - Perseids Meteor Shower"
                    "2024 September" -> "Fall Equinox September 24"
                    "2024 October" -> "October 22 - Orionids Meteor Shower"
                    "2024 November" -> "Mercury visible at Sunset until 30th"
                    "2024 December" -> "Winter Solstice Dec 21st"

                    "2025 January" -> "Mars at Opposition on January 16th"
                    "2025 February" -> "none 0"
                    "2025 March" -> "Spring Equinox March 20th"
                    "2025 April" -> "April 23rd - Lyrids Meteor Shower"
                    "2025 May" -> "May 7th - Eta Aquarids Meteor Shower"
                    "2025 June" -> "Summer Solstice June 20th"
                    "2025 July" -> "July 29th - Delta Aquarids Meteor Shower"
                    "2025 August" -> "August 13th - Perseids Meteor Shower"
                    "2025 September" -> "Fall Equinox: September 22nd"
                    "2025 October" -> "October 22nd - Orionids Meteor Shower"
                    "2025 November" -> "November 5th - Taurids Meteor Shower"
                    "2025 December" -> "Winter Solstice : December 21st"


                    "2026 March" -> "Spring Equinox March 20th"
                    "2026 June" -> "Summer Solstice June 21st"
                    "2026 September" -> "Fall Equinox: September 22nd"
                    "2026 December" -> "Winter Solstice : December 21st"

                    "2027 March" -> "Spring Equinox March 20th"
                    "2027 June" -> "Summer Solstice June 21st"
                    "2027 September" -> "Fall Equinox: September 22nd"
                    "2027 December" -> "Winter Solstice : December 21st"

                    "2028 March" -> "Spring Equinox March 19th"
                    "2028 June" -> "Summer Solstice June 20th"
                    "2028 September" -> "Fall Equinox: September 22nd"
                    "2028 December" -> "Winter Solstice : December 21st"

                    "2029 March" -> "Spring Equinox March 20th"
                    "2029 June" -> "Summer Solstice June 20th"
                    "2029 September" -> "Fall Equinox: September 22nd"
                    "2029 December" -> "Winter Solstice : December 21st"

                    "2030 March" -> "Spring Equinox March 20th"
                    "2030 June" -> "Summer Solstice June 21st"
                    "2030 September" -> "Fall Equinox: September 22nd"
                    "2030 December" -> "Winter Solstice : December 21st"

                    else -> "none 0"
                }
            return planetOpposition

        }
        private fun getPlanetEvent3(): String {
            val planetOpposition =
                when(getHoroscope()){
                    "Aries" -> "Monthly Ruling Planet: Mars"
                    "Taurus" -> "Monthly Ruling Planet: Venus"
                    "Gemini" -> "Monthly Ruling Planet: Mercury"
                    "Cancer" -> "Ruling in Sky: Moon"
                    "Leo" -> "Ruling in Sky: Sun"
                    "Virgo" -> "Monthly Ruling Planet: Mercury"
                    "Libra" -> "Monthly Ruling Planet: Venus"
                    "Scorpio" ->"Monthly Ruling Planet: Pluto"
                    "Sagittarius" -> "Monthly Ruling Planet: Jupiter"
                    "Capricorn" -> "Monthly Ruling Planet: Saturn"
                    "Aquarius" -> "Monthly Ruling Planet: Uranus"
                    "Pisces" -> "Monthly Ruling Planet: Neptune"
                    else -> "Monthly Ruling Planet: Saturn"
                }
            return planetOpposition

        }
        private fun getPlanetEventTYPE(x: String ): String {

            val planetType = when{

                x.contains("Moon") -> "moon"
                x.contains("Solstice" )-> "sun"
                x.contains("Equinox")-> "sun"
                x.contains("solstice")-> "sun"
                x.contains("equinox")-> "sun"
                x.contains("Mercury")-> "mercury"
                x.contains("Venus")-> "venus"
                x.contains("Mars")-> "mars"
                x.contains("Jupiter")-> "jupiter"
                x.contains("Saturn")-> "saturn"
                x.contains("Uranus")-> "uranus"
                x.contains("Neptune")-> "neptune"
                x.contains("Pluto")-> "pluto"
                x.contains("Meteor")-> "shower"
                x.contains("meteor")-> "shower"
                else -> "none"
            }

            return planetType
        }





        private fun getHoroscope(): String {

            val sdf2 = SimpleDateFormat("MMMM")
            val sdf3 = SimpleDateFormat("d")
            val d = Date()
            val monthOfYear: String = sdf2.format(d)
            val dayOfMonth: String = sdf3.format(d)

            val horoscopeString = when(monthOfYear){
                "January" -> if(Integer.parseInt(dayOfMonth) in 1..19){ "Capricorn" }
                else {"Aquarius" }
                "February" ->  if(Integer.parseInt(dayOfMonth) in 1..18 ){"Aquarius"}
                else {"Pisces"}
                "March" -> if(Integer.parseInt(dayOfMonth) in 1..20 ){"Pisces"}
                else{ "Aries"}
                "April" -> if(Integer.parseInt(dayOfMonth) in 1..19 ){"Aries"}
                else {"Taurus"}
                "May" -> {"Taurus"}
                "June" -> if(Integer.parseInt(dayOfMonth) in 1..20 ){"Gemini"}
                else{"Cancer"}
                "July" -> if(Integer.parseInt(dayOfMonth) in 1..22) {"Cancer"}
                else {"Leo"}
                "August" ->if(Integer.parseInt(dayOfMonth) in 1..22){ "Leo"}
                else {"Virgo"}
                "September" -> if(Integer.parseInt(dayOfMonth) in 1..22) {"Virgo"}
                else{"Libra"}
                "October" -> if(Integer.parseInt(dayOfMonth) in 1..22) {"Libra"}
                else {"Scorpio"}
                "November" ->if(Integer.parseInt(dayOfMonth) in 1..21) { "Scorpio"}
                else {"Sagittarius"}
                "December" -> if(Integer.parseInt(dayOfMonth) in 1..21) { "Sagittarius"}
                else{ "Capricorn"}
                else -> "Cancer" }
            return horoscopeString
        }
        private fun getDayorNight(): String {
            val sdf = SimpleDateFormat("k")
            val d = Date()
            val militaryTime: String = sdf.format(d)

            val timeTypeString = when (Integer.parseInt(militaryTime)){
                in 0..5 -> "Night"
                in 6..18 -> "Day"
                in 19..23 -> "Night"
                else-> "Night"
            }
            return timeTypeString
        }

        val frameTime = INTERACTIVE_UPDATE_RATE_MS


        private fun getAstrologyBackground(): Bitmap {

        val frameTime = INTERACTIVE_UPDATE_RATE_MS
        val sdf2 = SimpleDateFormat("MMMM")
            val d = Date()
            val monthOfYear: String = sdf2.format(d)
            val backgroundBitmap: Bitmap =
               when (counter) {
                    0-> when (getHoroscope()) {
                        "Aquarius" -> BitmapFactory.decodeResource(resources, R.drawable.aquarius)
                        "Aries" -> BitmapFactory.decodeResource(resources, R.drawable.aries)
                        "Cancer" -> BitmapFactory.decodeResource(resources, R.drawable.cancer)
                        "Capricorn" -> BitmapFactory.decodeResource(resources, R.drawable.capricorn)
                        "Gemini" -> BitmapFactory.decodeResource(resources, R.drawable.gemini)
                        "Leo" -> BitmapFactory.decodeResource(resources, R.drawable.leo)
                        "Libra" -> BitmapFactory.decodeResource(resources, R.drawable.libra)
                        "Pisces" -> BitmapFactory.decodeResource(resources, R.drawable.pisces)
                        "Sagittarius" -> BitmapFactory.decodeResource(resources, R.drawable.sagitarius)
                        "Scorpio" -> BitmapFactory.decodeResource(resources, R.drawable.scorpio)
                        "Taurus" -> BitmapFactory.decodeResource(resources, R.drawable.taurus)
                        "Virgo" -> BitmapFactory.decodeResource(resources, R.drawable.virgo)
                        else -> BitmapFactory.decodeResource(resources, R.drawable.cancer) }
                    1->  BitmapFactory.decodeResource(resources, R.drawable.astronomicalclockbg)
                    2->  BitmapFactory.decodeResource(resources, R.drawable.astronomicalclockbg)
                    3 -> when(getMoonPhase()){
                        "New Moon" -> BitmapFactory.decodeResource(resources, R.drawable.newmoon)
                        "Waxing Crescent Moon" -> BitmapFactory.decodeResource(resources, R.drawable.rightcrescent)
                        "Waxing Half Moon" -> BitmapFactory.decodeResource(resources, R.drawable.halfmoonright)
                        "Waxing Gibbous Moon" -> BitmapFactory.decodeResource(resources, R.drawable.gibright)
                        "Full Moon" -> BitmapFactory.decodeResource(resources, R.drawable.fulloon)
                        "Waning Gibbous Moon" -> BitmapFactory.decodeResource(resources, R.drawable.gibleft)
                        "Waning Half Moon" -> BitmapFactory.decodeResource(resources, R.drawable.halfmoonleft)
                        "Waning Crescent Moon" -> BitmapFactory.decodeResource(resources, R.drawable.leftcrescent)
                        else-> BitmapFactory.decodeResource(resources, R.drawable.plainmoon)
                    }
                    4 -> when(getPlanetEventTYPE(filterPlanetNews())){
                        "meteor"-> BitmapFactory.decodeResource(resources, R.drawable.shower)
                        "sun"-> BitmapFactory.decodeResource(resources, R.drawable.sun)
                        "mercury"-> BitmapFactory.decodeResource(resources, R.drawable.mercury)
                        "venus"-> BitmapFactory.decodeResource(resources, R.drawable.venus)
                        "mars"-> BitmapFactory.decodeResource(resources, R.drawable.mars)
                        "jupiter"-> BitmapFactory.decodeResource(resources, R.drawable.jupiter)
                        "saturn"-> BitmapFactory.decodeResource(resources, R.drawable.saturn)
                        "uranus"-> BitmapFactory.decodeResource(resources, R.drawable.uranus)
                        "neptune"-> BitmapFactory.decodeResource(resources, R.drawable.neptune)
                        "pluto"-> BitmapFactory.decodeResource(resources, R.drawable.pluto1)
                        "none" -> when (getDayorNight()){
                            "Day" -> BitmapFactory.decodeResource(resources, R.drawable.sun)
                            "Night" -> BitmapFactory.decodeResource(resources, R.drawable.shower)
                            else -> BitmapFactory.decodeResource(resources, R.drawable.shower) }
                        else -> BitmapFactory.decodeResource(resources, R.drawable.shower)}

                   5 -> when(getPlanetEventTYPE(filterPlanetNews2())){
                       "meteor"-> BitmapFactory.decodeResource(resources, R.drawable.shower)
                       "sun"-> BitmapFactory.decodeResource(resources, R.drawable.sun)
                       "mercury"-> BitmapFactory.decodeResource(resources, R.drawable.mercury)
                       "venus"-> BitmapFactory.decodeResource(resources, R.drawable.venus)
                       "mars"-> BitmapFactory.decodeResource(resources, R.drawable.mars)
                       "jupiter"-> BitmapFactory.decodeResource(resources, R.drawable.jupiter)
                       "saturn"-> BitmapFactory.decodeResource(resources, R.drawable.saturn)
                       "uranus"-> BitmapFactory.decodeResource(resources, R.drawable.uranus)
                       "neptune"-> BitmapFactory.decodeResource(resources, R.drawable.neptune)
                       "pluto"-> BitmapFactory.decodeResource(resources, R.drawable.pluto1)
                       "none" -> when (getDayorNight()){
                           "Day" -> BitmapFactory.decodeResource(resources, R.drawable.sun)
                           "Night" -> BitmapFactory.decodeResource(resources, R.drawable.shower)
                           else -> BitmapFactory.decodeResource(resources, R.drawable.sun) }
                       else -> BitmapFactory.decodeResource(resources, R.drawable.sun)}

                    6 -> when(getPlanetEventTYPE(getPlanetEvent3())){
                        "moon"-> BitmapFactory.decodeResource(resources, R.drawable.plainmoon)
                        "sun"-> BitmapFactory.decodeResource(resources, R.drawable.sun)
                        "mercury"-> BitmapFactory.decodeResource(resources, R.drawable.mercury)
                        "venus"-> BitmapFactory.decodeResource(resources, R.drawable.venus)
                        "mars"-> BitmapFactory.decodeResource(resources, R.drawable.mars)
                        "jupiter"-> BitmapFactory.decodeResource(resources, R.drawable.jupiter)
                        "saturn"-> BitmapFactory.decodeResource(resources, R.drawable.saturn)
                        "uranus"-> BitmapFactory.decodeResource(resources, R.drawable.uranus)
                        "neptune"-> BitmapFactory.decodeResource(resources, R.drawable.neptune)
                        else -> BitmapFactory.decodeResource(resources, R.drawable.sun)}

                    7 -> when(monthOfYear){
                        "January"-> BitmapFactory.decodeResource(resources, R.drawable.moonwolf)
                        "February"-> BitmapFactory.decodeResource(resources, R.drawable.moonsnow)
                        "March"-> BitmapFactory.decodeResource(resources, R.drawable.moonworm)
                        "April"-> BitmapFactory.decodeResource(resources, R.drawable.moonpink)
                        "May"-> BitmapFactory.decodeResource(resources, R.drawable.moonpink)
                        "June"-> BitmapFactory.decodeResource(resources, R.drawable.moonstrawberry)
                        "July"-> BitmapFactory.decodeResource(resources, R.drawable.moonanimal)
                        "August"-> BitmapFactory.decodeResource(resources, R.drawable.moonanimal)
                        "September"-> BitmapFactory.decodeResource(resources, R.drawable.mooncorn)
                        "October"-> BitmapFactory.decodeResource(resources, R.drawable.moonharvest)
                        "November"-> BitmapFactory.decodeResource(resources, R.drawable.moonbeaver)
                        "December"-> BitmapFactory.decodeResource(resources, R.drawable.mooncold)
                        else -> BitmapFactory.decodeResource(resources, R.drawable.sun)}

                   8-> when (getDayorNight()){
                       "Day" -> BitmapFactory.decodeResource(resources, R.drawable.sun)
                       "Night" -> BitmapFactory.decodeResource(resources, R.drawable.shower)
                       else -> BitmapFactory.decodeResource(resources, R.drawable.sun) }

                    else -> BitmapFactory.decodeResource(resources, R.drawable.sun)
                }
            return backgroundBitmap
        }

        private fun getAnimationCase(): String {


            val sdf = SimpleDateFormat("EEE")
            val sdf1 = SimpleDateFormat("EEEE")
            val sdf2 = SimpleDateFormat("MMMM")
            val sdf3 = SimpleDateFormat("d")
            val sdf4 = SimpleDateFormat("yyyy")
            val sdf5 = SimpleDateFormat("MMMM d yyyy")

            val d = Date()
            val dayOfTheWeek: String = sdf.format(d)
            val dayOfTheWeekLong: String = sdf1.format(d)
            val monthOfYear: String = sdf2.format(d)
            val dayOfMonth: String = sdf3.format(d)
            val year4digits: String = sdf4.format(d)
            val fullDateSpaces: String = sdf5.format(d)

            val ThanksgivingArray = arrayOf(
                "November 24 2022",
                "November 23 2023",
                "November 28 2024",
                "November 27 2025",
                "November 26 2026",
                "November 25 2027",
                "November 23 2028",
                "November 22 2029",
                "November 28 2030",
                "November 27 2031",
                "November 25 2032"
            )

            val lunarArray = arrayOf(
                "February 1 2022",
                "January 22 2023",
                "February 10 2024",
                "January 29 2025",
                "February 17 2026",
                "February 7 2027",
                "January 26 2028",
                "February 13 2029",
                "February 2 2030"
            )

                val easterArray = arrayOf(
                "April 17 2022",
                "April 9 2023",
                "March 31 2024",
                "April 20 2025",
                "April 5 2026",
                "March 28 2027",
                "April 16 2028",
                "April 1 2029",
                "April 21 2030",
                "April 13 2031",
                "March 28 2032"
            )
            val caseString =
            if (monthOfYear == "October") {
                "Halloween is on October 31st"
            } else if (monthOfYear == "November") {
                    if (Integer.parseInt(year4digits) in 2022..2032){
                ("Thanksgiving is on " + ThanksgivingArray[Integer.parseInt(year4digits)-2022])}
                    else{"Happy Fall!"}
            } else if (monthOfYear == "December") {
                //https://www.calendardate.com/hanukkah_2030.htm has dates up to 2030 for Hanukah or use HebrewCalendar (YEAR, 2, 25)
                if ((Integer.parseInt(year4digits) == 2022 && Integer.parseInt(dayOfMonth) in 18..23) ||
                    (Integer.parseInt(year4digits) == 2023 && Integer.parseInt(dayOfMonth) in 7..15) ||
                    (Integer.parseInt(year4digits) == 2024 && Integer.parseInt(dayOfMonth) in 26..30) ||
                    (Integer.parseInt(year4digits) == 2025 && Integer.parseInt(dayOfMonth) in 14..22) ||
                    (Integer.parseInt(year4digits) == 2026 && Integer.parseInt(dayOfMonth) in 4..12) ||
                    (Integer.parseInt(year4digits) == 2027 && Integer.parseInt(dayOfMonth) in 26..30) ||
                    (Integer.parseInt(year4digits) == 2028 && Integer.parseInt(dayOfMonth) in 12..20) ||
                    (Integer.parseInt(year4digits) == 2029 && Integer.parseInt(dayOfMonth) in 1..9) ||
                    (Integer.parseInt(year4digits) == 2030 && Integer.parseInt(dayOfMonth) in 20..23)
                ) {
                    "Happy Holidays!"
                } else if (dayOfMonth == "31"){"Happy New Year's Eve!"}
                else {
                    "It's Christmas Season!"
                }
            } else if (monthOfYear == "January"){
                if (lunarArray.contains(fullDateSpaces)) {
                    "Happy Lunar New Year!"
                }
                else if(dayOfMonth == "1") {
            "Happy New Year!"}
                else{"Winter Season"}
            }
            else if (monthOfYear == "February") {
                if (Integer.parseInt(dayOfMonth) in 1..14) {
                    "Valentine's Day is on February 14th"
                } else {
                    "Happy Spring"
                }
            } else if (monthOfYear == "March") {
                if (Integer.parseInt(dayOfMonth) in 1..17) {
                    "Saint Patrick's Day is on March 17th"
                } else if (easterArray.contains(fullDateSpaces)) {
                    "Happy Easter!"
                } else {
                    "Happy Spring!"
                }
            } else if (monthOfYear == "April") {
                if (easterArray.contains(fullDateSpaces)) {
                    "Happy Easter!"
                } else {
                    "Happy Spring!"
                }}
            else {
                (dayOfTheWeek + " , " + fullDateSpaces)
                }



            return caseString
        }


        private fun drawPrague(canvas: Canvas, bounds: Rect) {
            if (!mAmbient) {
            val sdf2 = SimpleDateFormat("MMMM")
            val d = Date()
            val monthOfYear: String = sdf2.format(d)
            var drawable = R.drawable.prague;

            val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

            val src = Rect(0, 0, bitmap.height, bitmap.width)
            val dst = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

            canvas.drawBitmap(
                bitmap,
                src,
                dst,
                null
            )}else{}
        }

        private fun draw12fix(canvas: Canvas, bounds: Rect) {

            val sdf2 = SimpleDateFormat("MMMM")
            val d = Date()
            val monthOfYear: String = sdf2.format(d)
            var drawable = R.drawable.numbers12hour
            if (!mAmbient) {
            val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

            val src = Rect(0, 0, bitmap.height, bitmap.width)
            val dst = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

            canvas.drawBitmap(
                bitmap,
                src,
                dst,
                null
            )}else{}
        }

        private fun drawPHHANDS(canvas: Canvas, bounds: Rect) {

            val sdf2 = SimpleDateFormat("K")
            val d = Date()
            val hour: String = sdf2.format(d)

            var drawable : Int = when (hour){
                "0" ->R.drawable.phstart
                "1" ->R.drawable.ph1
                "2" ->R.drawable.ph2
                "3" ->R.drawable.ph3
                "4" ->R.drawable.ph4
                "5" ->R.drawable.ph5
                "6" ->R.drawable.ph6
                "7" ->R.drawable.ph7
                "8" ->R.drawable.ph8
                "9" ->R.drawable.ph9
                "10" ->R.drawable.ph10
                "11" ->R.drawable.ph11
                "12" ->R.drawable.ph12
                "13" ->R.drawable.ph13
                "14" ->R.drawable.ph14
                "15" ->R.drawable.ph15
                "16" ->R.drawable.ph16
                "17" ->R.drawable.ph17
                "18" ->R.drawable.ph18
                "19" ->R.drawable.ph19
                "20" ->R.drawable.ph20
                "21" ->R.drawable.ph21
                "22" ->R.drawable.ph22
                "23" ->R.drawable.ph23
                "24" ->R.drawable.phstart
                else -> R.drawable.phstart}
            if (!mAmbient) {
            val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

            val src = Rect(0, 0, bitmap.height, bitmap.width)
            val dst = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

            canvas.drawBitmap(
                bitmap,
                src,
                dst,
                null
            )}else{}
        }

        private fun drawPWeek(canvas: Canvas, bounds: Rect) {
            if (!mAmbient) {
            val sdf2 = SimpleDateFormat("u")
            val d = Date()
            val weekday: String = sdf2.format(d)
            var drawable : Int = when (weekday){
                "1" ->R.drawable.pweek
                "2" ->R.drawable.wheeltues
                "3" ->R.drawable.wheelwed
                "4" ->R.drawable.wheelthurs
                "5" ->R.drawable.wheelfri
                "6" ->R.drawable.wheelsat
                "7" ->R.drawable.wheelsun
                else -> R.drawable.pweek}

            val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

            val src = Rect(0, 0, bitmap.height, bitmap.width)
            val dst = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

            canvas.drawBitmap(
                bitmap,
                src,
                dst,
                null
            )}else{}
        }



        private fun drawPdate(canvas: Canvas, bounds: Rect) {
            if (!mAmbient) {
            val sdf2 = SimpleDateFormat("d")
            val d = Date()
            val dated: String = sdf2.format(d)
            var drawable : Int = when (dated){
                "0" ->R.drawable.phstart
                "1" ->R.drawable.w1
                "2" ->R.drawable.w2
                "3" ->R.drawable.w3
                "4" ->R.drawable.w4
                "5" ->R.drawable.w5
                "6" ->R.drawable.w6
                "7" ->R.drawable.w7
                "8" ->R.drawable.w8
                "9" ->R.drawable.w9
                "10" ->R.drawable.w10
                "11" ->R.drawable.w11
                "12" ->R.drawable.w12
                "13" ->R.drawable.w13
                "14" ->R.drawable.w14
                "15" ->R.drawable.w15
                "16" ->R.drawable.w16
                "17" ->R.drawable.w17
                "18" ->R.drawable.w18
                "19" ->R.drawable.w19
                "20" ->R.drawable.w20
                "21" ->R.drawable.w21
                "22" ->R.drawable.w22
                "23" ->R.drawable.w23
                "24" ->R.drawable.w24
                "25" ->R.drawable.w25
                "26" ->R.drawable.w26
                else -> R.drawable.pdate}

            val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

            val src = Rect(0, 0, bitmap.height, bitmap.width)
            val dst = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

            canvas.drawBitmap(
                bitmap,
                src,
                dst,
                null
            )}else{}
        }

        private fun drawPointerMonth(canvas: Canvas, bounds: Rect) {
            if (!mAmbient) {
            val sdf2 = SimpleDateFormat("M")
            val d = Date()
            val monthOfYear: String = sdf2.format(d)
            var drawable : Int = when (monthOfYear){
                "0" ->R.drawable.p1
                "1" ->R.drawable.p1
                "2" ->R.drawable.p2
                "3" ->R.drawable.p3
                "4" ->R.drawable.p4
                "5" ->R.drawable.p5
                "6" ->R.drawable.p6
                "7" ->R.drawable.p7
                "8" ->R.drawable.p8
                "9" ->R.drawable.p9
                "10" ->R.drawable.p10
                "11" ->R.drawable.p11
                "12" ->R.drawable.p12
                else -> R.drawable.p1}


            val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

            val src = Rect(0, 0, bitmap.height, bitmap.width)
            val dst = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

            canvas.drawBitmap(
                bitmap,
                src,
                dst,
                null
            )}else{}
        }

        private fun drawPointer(canvas: Canvas, bounds: Rect) {
            if (!mAmbient) {
            val sdf2 = SimpleDateFormat("MMMM")
            val d = Date()

            var drawable = R.drawable.datepoint;

            val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

            val src = Rect(0, 0, bitmap.height, bitmap.width)
            val dst = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

            canvas.drawBitmap(
                bitmap,
                src,
                dst,
                null
            )}else{}
        }

        fun AmbientText() {

            if (mAmbient) {
            }
        }

        private fun drawHeartRates(canvas: Canvas, bounds: Rect) {
            val yourHeart = heartRate.roundToInt()

            var drawable : Int = when (yourHeart % 10){
                0 ->R.drawable.heartrateh0
                1 ->R.drawable.heartrateh1
                2 ->R.drawable.heartrateh2
                3 ->R.drawable.heartrateh3
                4 ->R.drawable.heartrateh4
                5 ->R.drawable.heartrateh5
                6 ->R.drawable.heartrateh6
                7 ->R.drawable.heartrateh7
                8 ->R.drawable.heartrateh8
                9 ->R.drawable.heartrateh9
                else -> R.drawable.heartrateh0}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left  , bounds.top , bounds.right   , bounds.bottom )

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }
        private fun drawHeartRatesTens(canvas: Canvas, bounds: Rect) {

            val yourHeart = heartRate.roundToInt()

            var drawable : Int = when ((floor(((yourHeart % 100) / 10).toDouble()).toInt())){
                0 ->R.drawable.heartrateh0
                1 ->R.drawable.heartrateh1
                2 ->R.drawable.heartrateh2
                3 ->R.drawable.heartrateh3
                4 ->R.drawable.heartrateh4
                5 ->R.drawable.heartrateh5
                6 ->R.drawable.heartrateh6
                7 ->R.drawable.heartrateh7
                8 ->R.drawable.heartrateh8
                9 ->R.drawable.heartrateh9
                else -> R.drawable.heartrateh0}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left  , bounds.top , bounds.right -25  , bounds.bottom )

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun drawHeartRatesHundreds(canvas: Canvas, bounds: Rect) {

            val yourHeart = heartRate.roundToInt()

            var drawable : Int = when (floor((yourHeart / 100).toDouble()).toInt()){
                0 ->R.drawable.heartrateh0
                1 ->R.drawable.heartrateh1
                2 ->R.drawable.heartrateh2
                3 ->R.drawable.heartrateh3
                4 ->R.drawable.heartrateh4
                5 ->R.drawable.heartrateh5
                6 ->R.drawable.heartrateh6
                7 ->R.drawable.heartrateh7
                8 ->R.drawable.heartrateh8
                9 ->R.drawable.heartrateh9
                else -> R.drawable.heartrateh0}
            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left  , bounds.top , bounds.right -47  , bounds.bottom )

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }
        private fun drawDates(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("d")
            val d = Date()
            val day: String = sdf.format(d)

            var drawable : Int = when( Integer.parseInt(day)%10){
                0 ->R.drawable.dateh0
                1 ->R.drawable.dateh1
                2 ->R.drawable.dateh2
                3 ->R.drawable.dateh3
                4 ->R.drawable.dateh4
                5 ->R.drawable.dateh5
                6 ->R.drawable.dateh6
                7 ->R.drawable.dateh7
                8 ->R.drawable.dateh8
                9 ->R.drawable.dateh9
                else -> R.drawable.dateh0}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left  , bounds.top, bounds.right   , bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun drawDatesTen(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("d")
            val d = Date()
            val day: String = sdf.format(d)

            var drawable: Int = when ((floor(((Integer.parseInt(day) / 10)).toDouble()).toInt())) {
                0 -> R.drawable.dateh0
                1 -> R.drawable.dateh1
                2 -> R.drawable.dateh2
                3 -> R.drawable.dateh3
                4 -> R.drawable.dateh4
                5 -> R.drawable.dateh5
                6 -> R.drawable.dateh6
                7 -> R.drawable.dateh7
                8 -> R.drawable.dateh8
                9 -> R.drawable.dateh9
                else -> R.drawable.dateh0
            }


            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left , bounds.top, bounds.right - 18, bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            } else {
            }
        }

        private fun drawMonths(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("M")
            val d = Date()
            val month: String = sdf.format(d)

            var drawable : Int = when( Integer.parseInt(month)%10){
                0 ->R.drawable.dateh0
                1 ->R.drawable.dateh1
                2 ->R.drawable.dateh2
                3 ->R.drawable.dateh3
                4 ->R.drawable.dateh4
                5 ->R.drawable.dateh5
                6 ->R.drawable.dateh6
                7 ->R.drawable.dateh7
                8 ->R.drawable.dateh8
                9 ->R.drawable.dateh9
                else -> R.drawable.dateh0}

            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left  , bounds.top, bounds.right-40   , bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            }else{}
        }

        private fun drawMonthsTen(canvas: Canvas, bounds: Rect) {
            val sdf = SimpleDateFormat("M")
            val d = Date()
            val month: String = sdf.format(d)

            var drawable: Int = when ((floor(((Integer.parseInt(month) / 10)).toDouble()).toInt())) {
                0 -> R.drawable.blank
                1 -> R.drawable.dateh1
                else -> R.drawable.blank
            }


            if (mAmbient) {
                val bitmap = BitmapFactory.decodeResource(applicationContext.resources, drawable)

                val src = Rect(0, 0, bitmap.height, bitmap.width)
                val dst = Rect(bounds.left , bounds.top, bounds.right - 59, bounds.bottom)

                canvas.drawBitmap(
                    bitmap,
                    src,
                    dst,
                    null
                )
            } else {
            }
        }




        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
    override fun onSensorChanged(event: SensorEvent?) {
        val mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        if (event?.sensor == mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)) {
            event?.values?.get(0)?.let {
                heartRate = it
            }
        }

        if (event?.sensor == mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)) {
            event?.values?.get(0)?.let {
                stepCount = it
            }
        }
    }

    override fun onAccuracyChanged(event: Sensor?, p1: Int) {
    }
}