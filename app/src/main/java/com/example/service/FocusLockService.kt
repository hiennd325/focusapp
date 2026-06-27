package com.example.service

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.FocusDatabase
import com.example.data.FocusRepository
import kotlinx.coroutines.*

class FocusLockService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private lateinit var repository: FocusRepository

    private val blacklist = listOf(
        "com.facebook.katana",      // Facebook
        "com.instagram.android",    // Instagram
        "com.zhiliaoapp.musically", // TikTok
        "com.twitter.android",      // X/Twitter
        "com.android.youtube",      // YouTube
        "com.google.android.youtube",
        "com.android.chrome"        // Chrome
    )

    override fun onCreate() {
        super.onCreate()
        val dao = FocusDatabase.getDatabase(this).focusDao()
        repository = FocusRepository(dao)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_FOCUS") {
            stopFocusService()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Steel discipline mode is active..."))
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                val activeSession = repository.getActiveSession()
                if (activeSession == null) {
                    // No active focus session, stop the service
                    withContext(Dispatchers.Main) {
                        stopFocusService()
                    }
                    break
                }

                // Check foreground app
                val foregroundApp = getForegroundAppPackage()
                if (foregroundApp != null && blacklist.contains(foregroundApp)) {
                    Log.d("FocusLockService", "Blocked app detected: $foregroundApp")
                    repository.logEvent("DISTRACTION", "Detected distraction app access: $foregroundApp")
                    
                    // Increment distraction count inside database
                    val updated = activeSession.copy(distractionCount = activeSession.distractionCount + 1)
                    repository.updateSession(updated)

                    // Bring FocusLock AI to front!
                    launchBlockingOverlay()
                }
                delay(1200) // Poll usage state every 1.2s
            }
        }
    }

    private fun getForegroundAppPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 30, time)
        if (!stats.isNullOrEmpty()) {
            val sorted = stats.sortedByDescending { it.lastTimeUsed }
            return sorted.firstOrNull()?.packageName
        }
        return null
    }

    private fun launchBlockingOverlay() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("BLOCKED_TRIGGER", true)
        }
        startActivity(intent)
    }

    private fun stopFocusService() {
        monitorJob?.cancel()
        stopForeground(true)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, FocusLockService::class.java).apply {
            action = "STOP_FOCUS"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 FocusLock AI - Deep Focus Mode")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Unlock Challenge", pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "FocusLock Active Session Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Displays the status notification for the deep focus lock."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "FocusLockServiceChannel"
        const val NOTIFICATION_ID = 1109
    }
}
