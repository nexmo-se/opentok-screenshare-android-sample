package com.nexmo.screensharecall

import android.util.Log
import com.opentok.android.BaseVideoCapturer
import java.nio.ByteBuffer

class CustomVideoCapturer : BaseVideoCapturer() {
    companion object {
        const val TAG = "CustomVideoCapturer"

        const val pixelFormat = ABGR;
    }

    private var width: Int = 240
    private var height: Int = 320
    private var isCapturing: Boolean = false

    override fun init() {
        Log.d(TAG, "On Init")
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
        captureSettings.fps = 25
        captureSettings.expectedDelay = 0

        return captureSettings
    }

    override fun startCapture(): Int {
        Log.d(TAG, "Start Capture")
        return 0
    }

    override fun stopCapture(): Int {
        Log.d(TAG, "Stop Capture")
        return 0
    }

    override fun isCaptureStarted(): Boolean {
        return isCapturing
    }

    override fun destroy() {
        Log.d(TAG, "Destroy")
    }

    fun sendFrame(imageBuffer: ByteBuffer, imageWidth: Int, imageHeight: Int) {
        provideBufferFrame(imageBuffer, pixelFormat, imageWidth, imageHeight, 0, false)
    }

}