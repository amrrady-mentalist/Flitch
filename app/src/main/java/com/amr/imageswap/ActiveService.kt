package com.amr.imageswap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Active mode. Keeps checking the word API every 2 seconds, even while you use other apps or the
 * screen is off. When a new word arrives it is sent to the email, then Active mode switches itself off.
 */
class ActiveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job?.isActive == true) return START_NOT_STICKY
        val prefs = Prefs(this)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Active mode", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val note = Notification.Builder(this).apply {
            if (Build.VERSION.SDK_INT >= 26) setChannelId(CHANNEL)
            setContentTitle("Glitch is active")
            setContentText("Waiting for the next word from the API")
            setSmallIcon(android.R.drawable.stat_notify_sync)
            setOngoing(true)
        }.build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, note)
        }

        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glitch:active")
            .apply { acquire(MAX_MS) }

        prefs.activeOn = true
        prefs.lastStatus = "Active: waiting for the word..."
        job = scope.launch { watch(prefs) }
        return START_NOT_STICKY
    }

    private suspend fun watch(prefs: Prefs) {
        val end = System.currentTimeMillis() + MAX_MS
        var last: String? = null
        while (scope.isActive && System.currentTimeMillis() < end) {
            val s = Net.selection(prefs.apiUrl, prefs.apiKey).getOrNull()
            if (s != null) {
                val sig = "${s.count}|${s.value}"
                if (last == null) {
                    last = sig // the word that was already there is ignored
                } else if (sig != last && s.value.isNotBlank()) {
                    val err = LiveSender.send(this, prefs, s.value)
                    if (err == null) {
                        prefs.lockedSlot = prefs.currentSlot
                        prefs.lastStatus = "Sent: ${s.value}"
                        vibrate(this, Buzz.OK)
                        stopSelf()
                        return
                    }
                    // Upload failed: say so and try the same word again on the next round.
                    prefs.lastStatus = err
                    vibrate(this, Buzz.FAIL)
                } else if (sig != last) {
                    last = sig
                }
            }
            delay(2000)
        }
        prefs.lastStatus = "Active mode timed out."
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        try {
            wake?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            // already released
        }
        val prefs = Prefs(this)
        prefs.activeOn = false
        if (prefs.lastStatus.startsWith("Active")) prefs.lastStatus = "Active mode stopped."
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "active"
        private const val MAX_MS = 60 * 60 * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ActiveService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ActiveService::class.java))
        }
    }
}
