package com.example.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class RadioTransferService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = pending
        val title = intent?.getStringExtra("title")
        if (request == null || title == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Radio transfers", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val openApp = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
            startForeground(1, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(title)
                .setContentText("Radio programming in progress")
                .setContentIntent(openApp)
                .setOngoing(true)
                .build())
            val power = getSystemService(PowerManager::class.java)
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChirpField:RadioTransfer")
                .apply { acquire(30 * 60 * 1000L) }
            request.complete(Unit)
        } catch (error: Exception) {
            request.completeExceptionally(error)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "radio_transfer"
        private val transfer = Mutex()
        private var pending: CompletableDeferred<Unit>? = null

        internal suspend fun <T> run(context: Context, title: String, operation: suspend () -> T): T =
            transfer.withLock {
                val ready = CompletableDeferred<Unit>()
                pending = ready
                val intent = Intent(context, RadioTransferService::class.java).putExtra("title", title)
                try {
                    ContextCompat.startForegroundService(context, intent)
                    withTimeout(10_000L) { ready.await() }
                    operation()
                } finally {
                    pending = null
                    context.stopService(intent)
                }
            }
    }
}
