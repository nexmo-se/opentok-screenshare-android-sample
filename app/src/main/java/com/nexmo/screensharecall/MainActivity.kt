package com.nexmo.screensharecall

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.opentok.android.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.nio.ByteBuffer
import java.util.*

class MainActivity : AppCompatActivity(), Session.SessionListener, PublisherKit.PublisherListener, MediaProjectionHandler {
    companion object {
        const val TAG = "MainActivity"

        const val API_KEY = "46481982"
        const val SESSION_ID =
            "2_MX40NjQ4MTk4Mn5-MTU5OTExNTMyNDE5NH5pSjBMTUVEeG84Q2lEMXVPelBvcDhKNVh-fg"
        const val TOKEN =
            "T1==cGFydG5lcl9pZD00NjQ4MTk4MiZzaWc9NzE4MDFjMjFkNTdmYzcwNDY3ZTcwODRjZTRmZDg5ZGQ5Zjc5NWY4NTpzZXNzaW9uX2lkPTJfTVg0ME5qUTRNVGs0TW41LU1UVTVPVEV4TlRNeU5ERTVOSDVwU2pCTVRVVkVlRzg0UTJsRU1YVlBlbEJ2Y0RoS05WaC1mZyZjcmVhdGVfdGltZT0xNTk5MTE1MzM1Jm5vbmNlPTAuNTE1OTU0OTQwMTEyNDYyMSZyb2xlPXB1Ymxpc2hlciZleHBpcmVfdGltZT0xNTk5MjAxNzM1JmluaXRpYWxfbGF5b3V0X2NsYXNzX2xpc3Q9"

        const val RC_VIDEO_APP_PERM = 124
        const val RC_SCREEN_CAPTURE = 125
    }

    private lateinit var session: Session


    private lateinit var publisherViewContainer: FrameLayout
    private lateinit var subscriberViewContainer: LinearLayout
    private lateinit var shareScreenButton: Button
    private lateinit var disconnectButton: Button

    private var customVideoCapturer: CustomVideoCapturer? = null

    private var publisherMain: Publisher? = null
    private var publisherScreen: Publisher? = null
    private var subscribers: MutableList<Subscriber> = ArrayList()

    private var mediaProjectionServiceIsBound: Boolean = false
    private var mediaProjectionBinder: MediaProjectionBinder? = null

    private val connection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service Disconnected")
            mediaProjectionBinder = null
            mediaProjectionServiceIsBound = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Connected")
            mediaProjectionServiceIsBound = true
            mediaProjectionBinder = service as MediaProjectionBinder
            mediaProjectionBinder?.mediaProjectionHandler = this@MainActivity
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()
    }

    override fun onDestroy() {
        disconnect(false)

        if (mediaProjectionServiceIsBound) {
            unbindService(connection)
        }
        super.onDestroy()
    }

    private fun disconnect(endActivity: Boolean) {
        unpublishCamera()
        unpublishScreen()
        session.disconnect()

        if (endActivity) {
            Log.d(TAG, "Ending Activity")
            this.finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_SCREEN_CAPTURE) {
            val intent = Intent(this, MediaProjectionService::class.java)
            intent.putExtra("resultCode", resultCode)
            intent.putExtra("data", data)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)

            publishScreen()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @AfterPermissionGranted(RC_VIDEO_APP_PERM)
    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE
        )

        if (EasyPermissions.hasPermissions(this, *perms)) {
            publisherViewContainer = findViewById(R.id.publisher_container)
            subscriberViewContainer = findViewById(R.id.subscriber_container)
            shareScreenButton = findViewById(R.id.sharescreen_button)
            disconnectButton = findViewById(R.id.disconnect_button)

            shareScreenButton.setOnClickListener {
                if (publisherScreen == null) {
                    Log.d(TAG, "Initiate Screenshare")
                    requestScreenCapture()
                    shareScreenButton.text = "Stop Screenshare"
                } else {
                    Log.d(TAG, "Ending Screenshare")
                    unpublishScreen()
                    shareScreenButton.text = "Start Screenshare"
                }
            }

            disconnectButton.setOnClickListener {
                disconnect(true)
            }

            session = Session.Builder(this, API_KEY, SESSION_ID).build()
            session.setSessionListener(this)
            session.connect(TOKEN)
        } else {
            EasyPermissions.requestPermissions(
                this,
                "This app needs access to your camera and mic to make video calls",
                RC_VIDEO_APP_PERM,
                *perms
            )
        }
    }

    private fun requestScreenCapture() {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()

        startActivityForResult(intent, 125)
    }

    private fun publishCamera() {
        if (publisherMain == null) {
            publisherMain = Publisher.Builder(this).build()
            publisherMain!!.setPublisherListener(this)

            publisherViewContainer.addView(publisherMain!!.view)

            if (publisherMain!!.view is GLSurfaceView) {
                (publisherMain!!.view as GLSurfaceView).setZOrderOnTop(true)
            }

            Log.d(TAG, "Publishing Camera")
            session.publish(publisherMain)
        }
    }

    private fun publishScreen() {
        if (customVideoCapturer == null) {
            Log.d(TAG, "Creating Custom Video Capturer")
            customVideoCapturer = CustomVideoCapturer()
        }

        if (publisherScreen == null) {
            Log.d(TAG, "Creating Publisher (Screen)")
            publisherScreen = Publisher.Builder(this)
                .capturer(customVideoCapturer)
                .build()
            publisherScreen!!.setPublisherListener(this)

            Log.d(TAG, "Publishing Screen")
            session.publish(publisherScreen)
        }
    }

    private fun unpublishCamera() {
        if (publisherMain != null) {
            Log.d(TAG, "Unpublishing Camera")
            session.unpublish(publisherMain)

            publisherMain?.capturer?.stopCapture()
            publisherMain = null
        }
    }

    private fun unpublishScreen() {
        if (publisherScreen != null) {
            Log.d(TAG, "Unpublishing Screen")
            session.unpublish(publisherScreen)

            publisherScreen?.capturer?.stopCapture()
            publisherScreen = null
        }
    }

    override fun onStreamDropped(p0: Session?, p1: Stream?) {
        Log.d(TAG, "Stream Dropped")
        subscriberViewContainer.removeAllViews()
    }

    override fun onStreamReceived(p0: Session?, p1: Stream?) {
        Log.d(TAG, "Stream Received")

        val subscriber = Subscriber.Builder(this, p1).build()
        session.subscribe(subscriber)
        subscribers.add(subscriber)

        val subscriberView = subscriber!!.view

        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = ViewGroup.LayoutParams(720, 1280)
        frameLayout.addView(subscriberView)
        subscriberViewContainer.addView(frameLayout)
    }

    override fun onConnected(p0: Session?) {
        Log.d(TAG, "Session Connected")
        publishCamera()
    }

    override fun onDisconnected(p0: Session?) {
        Log.d(TAG, "Session Disconnected")
    }

    override fun onError(p0: Session?, p1: OpentokError?) {
        Log.e(TAG, "Session Error: ${p1?.message ?: "null error message"}")
    }

    override fun onStreamCreated(p0: PublisherKit?, p1: Stream?) {
        Log.d(TAG, "Publisher Stream Created")
    }

    override fun onStreamDestroyed(p0: PublisherKit?, p1: Stream?) {
        Log.d(TAG, "Publisher Stream Destroyed")
    }

    override fun onError(p0: PublisherKit?, p1: OpentokError?) {
        Log.d(TAG, "Publisher Error: ${p1?.message ?: "null error message"}")
    }

    override fun sendFrame(imageBuffer: ByteBuffer, width: Int, height: Int) {
        customVideoCapturer?.sendFrame(imageBuffer, width, height)
    }

    override fun sendResize(width: Int, height: Int) {
        customVideoCapturer?.resize(width, height)
    }
}
