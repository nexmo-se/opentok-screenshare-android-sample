package com.nexmo.screensharecall

import android.Manifest
import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.projection.MediaProjectionManager
import android.opengl.GLSurfaceView
import android.os.BatteryManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentok.android.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.nio.ByteBuffer
import java.util.*

class MainActivity : AppCompatActivity(), Session.SessionListener, PublisherKit.PublisherListener, MediaProjectionHandler, SensorEventListener, TemperatureMonitorListener {
    companion object {
        const val TAG = "MainActivity"

        const val API_KEY = "46481982"
        const val SESSION_ID = "1_MX40NjQ4MTk4Mn5-MTYwMDY2MjkxMjk4M35YLzEyODZOczBXOGhqWEpTRTdleHF1UlB-fg"
        const val TOKEN = "T1==cGFydG5lcl9pZD00NjQ4MTk4MiZzaWc9Njg5MGU5OGExN2I0MzU1ZmVjZjI3NWQ1ZmM1ZTk3NDUzYzE5YzAzZTpzZXNzaW9uX2lkPTFfTVg0ME5qUTRNVGs0TW41LU1UWXdNRFkyTWpreE1qazRNMzVZTHpFeU9EWk9jekJYT0docVdFcFRSVGRsZUhGMVVsQi1mZyZjcmVhdGVfdGltZT0xNjAwNzYxMzczJm5vbmNlPTAuNDcyNjA4MTAxOTQ1MzE4MyZyb2xlPXB1Ymxpc2hlciZleHBpcmVfdGltZT0xNjAwODQ3NzczJmluaXRpYWxfbGF5b3V0X2NsYXNzX2xpc3Q9"

        const val RC_VIDEO_APP_PERM = 124
        const val RC_SCREEN_CAPTURE = 125
    }

    private lateinit var session: Session

    private lateinit var sensorManager: SensorManager
    private var ambientTemperatureSensor: Sensor? = null

    private lateinit var monitorContainer: LinearLayout
    private lateinit var publisherViewContainer: FrameLayout
    private lateinit var subscriberViewContainer: LinearLayout
    private lateinit var shareScreenButton: Button
    private lateinit var disconnectButton: Button

    private var temperatureMonitor: TemperatureMonitor? = null
    private var customVideoCapturer: CustomVideoCapturer? = null

    private var publisherMain: Publisher? = null
    private var publisherScreen: Publisher? = null
    private var subscribers: MutableList<Subscriber> = ArrayList()

    private var mediaProjectionServiceIsBound: Boolean = false
    private var mediaProjectionBinder: MediaProjectionBinder? = null

    private var startTime: Long = 0

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

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        ambientTemperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        if (ambientTemperatureSensor == null) {
            Log.d(TAG, "Sensor (Ambient) - not available, trying temperature sensor")
            ambientTemperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_TEMPERATURE)
            if (ambientTemperatureSensor == null) {
                Log.d(TAG, "Sensor (Temperature) - not available")
            }
        }

        startTime = System.currentTimeMillis()

        Log.d(TAG, "Registering Sensor Listener")
        sensorManager.registerListener(this, ambientTemperatureSensor, SensorManager.SENSOR_DELAY_NORMAL)

        Log.d(TAG, "Starting Temperature Monitor")
        temperatureMonitor = TemperatureMonitor(this)
        temperatureMonitor?.start(30, this)

        requestPermissions()
    }

    override fun onDestroy() {
        Log.d(TAG, "Stopping Temperature Monitor")
        temperatureMonitor?.stop()

        Log.d(TAG, "Unregistering Sensor Listener")
        sensorManager.unregisterListener(this)

        // On app killed, disconnect from session and unbind service
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
            // This is the result from requesting for Media Projection (screenshare) from Android OS

            // Bind to Foreground Service that does Media Projection
            // - Note: On newer Android versions, Media Projection need to be run on a Foreground Service.
            //         Regular Activity will cause it to crash.
            val intent = Intent(this, MediaProjectionService::class.java)
            intent.putExtra("resultCode", resultCode)
            intent.putExtra("data", data)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)

            // Setup Publisher for Screenshare and Publish to Session
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
            monitorContainer = findViewById(R.id.temperature_monitor_container)
            publisherViewContainer = findViewById(R.id.publisher_container)
            subscriberViewContainer = findViewById(R.id.subscriber_container)
            shareScreenButton = findViewById(R.id.sharescreen_button)
            disconnectButton = findViewById(R.id.disconnect_button)

            // Setup callback for Screenshare Button
            shareScreenButton.setOnClickListener {
                if (publisherScreen == null) {
                    // Screenshare is not on, so turn on screenshare
                    Log.d(TAG, "Initiate Screenshare")
                    requestScreenCapture()
                    shareScreenButton.text = "Stop Screenshare"
                } else {
                    // Screenshare is on, so turn off screenshare
                    Log.d(TAG, "Ending Screenshare")
                    unpublishScreen()
                    shareScreenButton.text = "Start Screenshare"
                }
            }

            // Setup callback for Disconnect Button
            disconnectButton.setOnClickListener {
                // Disconnect session
                disconnect(true)
            }

            // Create and Connect to Session
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

        // Send Intent to get Media Projection (screenshare)
        startActivityForResult(intent, 125)
    }

    private fun publishCamera() {
        // Create and publish camera
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
        // Create and publish screenshare
        if (customVideoCapturer == null) {
            Log.d(TAG, "Creating Custom Video Capturer")
            customVideoCapturer = CustomVideoCapturer()
        }

        if (publisherScreen == null) {
            Log.d(TAG, "Creating Publisher (Screen)")
            publisherScreen = Publisher.Builder(this)
                .capturer(customVideoCapturer)
                .build()
            publisherScreen!!.publisherVideoType = PublisherKit.PublisherKitVideoType.PublisherKitVideoTypeScreen
            publisherScreen!!.audioFallbackEnabled = false
            publisherScreen!!.setPublisherListener(this)

            Log.d(TAG, "Publishing Screen")
            session.publish(publisherScreen)
        }
    }

    private fun unpublishCamera() {
        // Unpublish Camera and Stop Capture (release hold on camera)
        if (publisherMain != null) {
            Log.d(TAG, "Unpublishing Camera")
            session.unpublish(publisherMain)

            publisherMain?.capturer?.stopCapture()
            publisherMain = null
        }
    }

    private fun unpublishScreen() {
        // Unpublish Screen and Stop Capture
        if (publisherScreen != null) {
            Log.d(TAG, "Unpublishing Screen")
            session.unpublish(publisherScreen)

            publisherScreen?.capturer?.stopCapture()
            publisherScreen = null
        }
    }

    override fun onStreamDropped(p0: Session?, p1: Stream?) {
        // When a stream is dropped from session, remove all stream views
        // TODO Note: You should keep track of all stream view and remove only the dropped stream view
        Log.d(TAG, "Stream Dropped")
        subscriberViewContainer.removeAllViews()
    }

    override fun onStreamReceived(p0: Session?, p1: Stream?) {
        Log.d(TAG, "Stream Received")

        // Subscribe to new stream
        val subscriber = Subscriber.Builder(this, p1)
            .build()
        subscriber.setVideoListener(object: SubscriberKit.VideoListener {
            override fun onVideoDataReceived(p0: SubscriberKit?) {
                Log.d(TAG, "onVideoDataReceived Received ${subscriber.stream.streamId}")
            }

            override fun onVideoEnabled(p0: SubscriberKit?, p1: String?) {
                Log.d(TAG, "onVideoEnabled Received ${subscriber.stream.streamId}")
            }

            override fun onVideoDisableWarning(p0: SubscriberKit?) {
                Log.d(TAG, "onVideoDisableWarning Received ${subscriber.stream.streamId}")
            }

            override fun onVideoDisableWarningLifted(p0: SubscriberKit?) {
                Log.d(TAG, "onVideoDisableWarningLifted Received ${subscriber.stream.streamId}")
            }

            override fun onVideoDisabled(p0: SubscriberKit?, p1: String?) {
                Log.d(TAG, "onVideoDisabled Received ${subscriber.stream.streamId}")
            }

        })


        session.subscribe(subscriber)
        subscribers.add(subscriber)

        val subscriberView = subscriber!!.view

        // Add stream view to screen
        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = ViewGroup.LayoutParams(p1?.videoWidth ?: 240, p1?.videoHeight ?: 320)
        frameLayout.addView(subscriberView)
        subscriberViewContainer.addView(frameLayout)
    }

    override fun onConnected(p0: Session?) {
        Log.d(TAG, "Session Connected")

        // Automatically publish the camera upon connected to session
//        publishCamera()
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "Sensor ($now) - Accuracy Changed - $accuracy")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val value = event?.values?.get(0) ?: -1
        val now = System.currentTimeMillis()
        Log.d(TAG, "Sensor ($now) - Sensor Changed - $value")
    }

    override fun onTemperature(temperature: Double) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "Sensor ($now) - Battery Temp - $temperature C")
    }
}
