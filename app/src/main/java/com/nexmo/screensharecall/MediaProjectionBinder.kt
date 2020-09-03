package com.nexmo.screensharecall

import android.os.Binder

class MediaProjectionBinder : Binder() {
    public var mediaProjectionHandler: MediaProjectionHandler? = null
}