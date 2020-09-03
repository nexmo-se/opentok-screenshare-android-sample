package com.nexmo.screensharecall

import java.nio.ByteBuffer

interface MediaProjectionHandler {
    fun sendFrame(imageBuffer: ByteBuffer, width: Int, height: Int)
    fun sendResize(width: Int, height: Int)
}