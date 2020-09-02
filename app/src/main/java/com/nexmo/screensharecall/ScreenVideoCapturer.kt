package com.nexmo.screensharecall

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.opentok.android.BaseVideoCapturer

class ScreenVideoCapturer(): BaseVideoCapturer(), ImageReader.OnImageAvailableListener {
    companion object {
        const val TAG = "ScreenVideoCapturer"
        const val SCREEN_CAPTURE_NAME = "screencapture"
        const val MAX_SCREEN_AXIS = 1024
        const val VIRTUAL_DISPLAY_FLAGS =
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    }

    private var width = 240
    private var height = 320
    private var density = 0
    private var isCapturing = false

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private lateinit var context: Context
    private lateinit var mediaProjection: MediaProjection

    constructor(context: Context, mediaProjection: MediaProjection): this() {
        this.context = context
        this.mediaProjection = mediaProjection

        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

        // Metrics
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)

        // Size
        val size = Point()
        display.getRealSize(size)
        resizeDisplaySizes(size.x, size.y)

        // Density
        density = metrics.densityDpi

        // Create Virtual Display
        createVirtualDisplay()
    }

    override fun init() {
        Log.d(TAG, "Init")
    }

    override fun onResume() {
        Log.d(TAG, "On Resume")
    }

    override fun onPause() {
        Log.d(TAG, "On Pause")
    }

    override fun getCaptureSettings(): CaptureSettings {
        val captureSettings = CaptureSettings()
        captureSettings.width = width
        captureSettings.height = height
        captureSettings.fps = 0
        captureSettings.expectedDelay = 0

        return captureSettings
    }

    override fun startCapture(): Int {
        Log.d(TAG, "Start Capture")
        isCapturing = true
        return 0
    }

    override fun stopCapture(): Int {
        Log.d(TAG, "Stop Capture")
        isCapturing = false
        return 0
    }

    override fun destroy() {
        Log.d(TAG, "Destroy")
    }

    override fun isCaptureStarted(): Boolean {
        return isCapturing
    }

    private fun resizeDisplaySizes(newWidth: Int, newHeight: Int){
        var multiplication = 1.0

        if (newHeight > MAX_SCREEN_AXIS) {
            multiplication = newHeight.toDouble() / MAX_SCREEN_AXIS
        }

        if (newWidth > MAX_SCREEN_AXIS && newWidth > newHeight) {
            multiplication = newWidth.toDouble() / MAX_SCREEN_AXIS
        }

        width = (newWidth.toDouble() / multiplication).toInt()
        height = (newHeight.toDouble() / multiplication).toInt()
    }


    private fun createVirtualDisplay() {
        Log.i(TAG, "Creating Virtual Display [$width x $height]")

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            SCREEN_CAPTURE_NAME,
            width,
            height,
            density,
            VIRTUAL_DISPLAY_FLAGS,
            imageReader!!.surface,
            null,
            null
        )
        try {
            val handler = Handler(Looper.getMainLooper())
            imageReader!!.setOnImageAvailableListener(this, handler)
        } catch (ex: Exception){
            Log.e("IMAGE_READER", "ERROR", ex)
        }

    }

    override fun onImageAvailable(reader: ImageReader?) {
        //Log.d(TAG, "Image Available")
        val image = reader?.acquireLatestImage() ?: return
        //Log.d(TAG, "Image OK")
        val arr = ByteArray(image.planes[0].buffer.remaining())
        image.planes[0].buffer.get(arr)
        provideByteArrayFrame(arr, ABGR, image.width, image.height, 0, false)

        image.close()
    }

}