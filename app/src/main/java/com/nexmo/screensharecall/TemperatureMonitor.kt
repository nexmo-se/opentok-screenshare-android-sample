package com.nexmo.screensharecall

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

class TemperatureMonitor(context: Context) {
    companion object {
        const val TAG = "TemperatureMonitor"
    }

    private val monitorRunnable = Runnable {
        Log.d(TAG, "Monitor Thread started")

        monitorShouldStop = false
        while (!monitorShouldStop) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempInt = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val tempFloat = 1.0 * tempInt / 10

            val now = System.currentTimeMillis()
            Log.d(TAG, "Sensor ($now) - Battery Temp - $tempFloat C")

            listener?.onTemperature(tempFloat)

            Thread.sleep(1000L * period)
        }

        Log.d(TAG, "Monitor Thread stopped")
    }

    private var monitorThread: Thread? = null
    private var monitorShouldStop = false

    private var period = 5
    private var listener: TemperatureMonitorListener? = null

    private fun startThread(period: Int, listener: TemperatureMonitorListener?) {
        if (monitorThread != null) {
            stopThread()
        }

        this.period = period
        this.listener = listener
        monitorThread = Thread(monitorRunnable)
        monitorThread?.start()
    }

    private fun stopThread() {
        val currentMonitorThread = monitorThread

        monitorShouldStop = true
        monitorThread = null

        currentMonitorThread?.join()
    }

    public fun start(period: Int = 5, listener: TemperatureMonitorListener? = null) {
        startThread(period, listener)
    }

    public fun stop() {
        stopThread()
    }
}