package com.nexmo.screensharecall

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.opentok.android.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.util.ArrayList

class MainActivity : AppCompatActivity(), Session.SessionListener, PublisherKit.PublisherListener {
    companion object {
        const val TAG = "MainActivity"

        const val API_KEY = "46481982"
        const val SESSION_ID = "2_MX40NjQ4MTk4Mn5-MTU5OTAyNzM1NzkzMn5lTm5JS25Db2dIL2kvWVNaOG11TDduK21-fg"
        const val TOKEN = "T1==cGFydG5lcl9pZD00NjQ4MTk4MiZzaWc9ZWRkODc1OTBkZjc4YTk2NGM2MmUwMDE4YTdkMjM0YTE0MTA2YTkwZTpzZXNzaW9uX2lkPTJfTVg0ME5qUTRNVGs0TW41LU1UVTVPVEF5TnpNMU56a3pNbjVsVG01SlMyNURiMmRJTDJrdldWTmFPRzExVERkdUsyMS1mZyZjcmVhdGVfdGltZT0xNTk5MDI3NDY5Jm5vbmNlPTAuOTY1Mjc5MTYwMDM0MTQxJnJvbGU9cHVibGlzaGVyJmV4cGlyZV90aW1lPTE1OTkxMTM4NjkmaW5pdGlhbF9sYXlvdXRfY2xhc3NfbGlzdD0="

        const val RC_VIDEO_APP_PERM = 124
        const val RC_SCREEN_CAPTURE = 125
    }

    private lateinit var session: Session


    private lateinit var publisherViewContainer: FrameLayout
    private lateinit var subscriberViewContainer: LinearLayout
    private lateinit var shareScreenButton: Button

    private var screenVideoCapturer: ScreenVideoCapturer? = null

    private var publisherMain: Publisher? = null
    private var publisherScreen: Publisher? = null
    private var subscribers: MutableList<Subscriber> = ArrayList()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()
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
            Log.d(TAG, "Projection Received")
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = projectionManager.getMediaProjection(resultCode, data!!)
            publishScreen(mediaProjection)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @AfterPermissionGranted(RC_VIDEO_APP_PERM)
    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (EasyPermissions.hasPermissions(this, *perms)) {
            publisherViewContainer = findViewById(R.id.publisher_container)
            subscriberViewContainer = findViewById(R.id.subscriber_container)
            shareScreenButton = findViewById(R.id.sharescreen_button)

            shareScreenButton.setOnClickListener {
                Log.d(TAG, "Initiate Share Screen")
                requestScreenCapture()
            }

            session = Session.Builder(this, API_KEY, SESSION_ID).build()
            session.setSessionListener(this)
            session.connect(TOKEN)
        } else {
            EasyPermissions.requestPermissions(this, "This app needs access to your camera and mic to make video calls", RC_VIDEO_APP_PERM, *perms)
        }
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
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

            session.publish(publisherMain)
        }
    }

    private fun publishScreen(mediaProjection: MediaProjection) {
        if (screenVideoCapturer == null) {
            Log.d(TAG, "Creating Screen Video Capturer")
            screenVideoCapturer = ScreenVideoCapturer(this, mediaProjection)
        }

        if (publisherScreen == null) {
            Log.d(TAG, "Creating Publisher (Screen)")
            publisherScreen = Publisher.Builder(this)
                .capturer(screenVideoCapturer)
                .build()
            publisherScreen!!.setPublisherListener(this)

            session.publish(publisherScreen)
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
}
