package com.example.silencerapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.silencerapp.data.AppDatabase
import com.example.silencerapp.data.StudySession
import com.example.silencerapp.ui.OverlayActivity
import kotlinx.coroutines.*
import android.util.Log
import android.widget.Toast
import android.media.AudioManager
import java.util.concurrent.TimeUnit

class FocusService : Service() {
    private var originalPolicy: NotificationManager.Policy? = null
    private var originalRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var timerJob: Job? = null
    private var monitorJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var remainingSeconds = 0L
    private var totalDurationSeconds = 0L
    private var distractingApps = mutableSetOf<String>()
    
    private var isBypassActive = false
    private var bypassEndTime = 0L
    private var bypassCount = 2 // Start with 2 bypasses
    private val MAX_BYPASSES = 2

    private val blockListReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "BLOCK_LIST_UPDATED") {
                distractingApps.clear()
                distractingApps.addAll(UsageHelper.getBlockedPackages(this@FocusService))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("FocusService", "onStartCommand: action=$action")
        
        if (action == "START") {
            Toast.makeText(this, "Silencer: Starting Session...", Toast.LENGTH_SHORT).show()
        }
        
        when (action) {
            "START" -> {
                val duration = intent?.getLongExtra("DURATION_MINUTES", 0L) ?: 0L
                startFocusSession(duration)
            }
            "STOP" -> stopFocusSession()
            "BYPASS" -> activateBypass(intent?.getIntExtra("BYPASS_MINUTES", 5) ?: 5)
            "GET_STATUS" -> broadcastStatus()
        }
        return START_STICKY
    }

    private fun startFocusSession(durationMinutes: Long) {
        // Enable Strict Do Not Disturb
        val notificationManager = getSystemService(NotificationManager::class.java)
        val audioManager = getSystemService(AudioManager::class.java)

        if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted) {
            // Save current settings
            originalPolicy = notificationManager.notificationPolicy
            originalRingerMode = audioManager.ringerMode

            // Apply Strict Policy: No sound, No visuals
            val strictPolicy = NotificationManager.Policy(
                0, // 0 means no priority categories allowed
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF or
                NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON or
                NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
                NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or
                NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or
                NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or
                NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST or
                NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR
            )
            
            notificationManager.notificationPolicy = strictPolicy
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            
            // Force Silent Mode
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            
            Toast.makeText(this, "Silencer: Strict Silence Active", Toast.LENGTH_SHORT).show()
        }

        Log.d("FocusService", "startFocusSession: duration=$durationMinutes")
        // Step 1: Immediate cleanup of existing jobs (DON'T call stopSelf)
        timerJob?.cancel()
        monitorJob?.cancel()
        try { unregisterReceiver(blockListReceiver) } catch (e: Exception) {}

        // Step 2: Initialize basic state
        remainingSeconds = durationMinutes * 60
        totalDurationSeconds = remainingSeconds
        bypassCount = MAX_BYPASSES
        isBypassActive = false
        
        // Step 3: IMMEDIATE Foreground activation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createNotification("Focus Session Active", formatTime(remainingSeconds)), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification("Focus Session Active", formatTime(remainingSeconds)))
        }

        // Step 4: Background work and broadcasts
        serviceScope.launch {
            distractingApps.clear()
            distractingApps.addAll(UsageHelper.getBlockedPackages(this@FocusService))
            Log.d("FocusService", "Distracting apps loaded: ${distractingApps.size}")
            
            withContext(Dispatchers.Main) {
                ContextCompat.registerReceiver(
                    this@FocusService,
                    blockListReceiver,
                    android.content.IntentFilter("BLOCK_LIST_UPDATED"),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                
                sendBroadcast(Intent("TIME_UPDATE").apply {
                    setPackage(packageName)
                    putExtra("SECONDS", remainingSeconds)
                    putExtra("TOTAL_SECONDS", totalDurationSeconds)
                    putExtra("BYPASS_COUNT", bypassCount)
                })
            }
        }

        timerJob = serviceScope.launch {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
                updateNotification(formatTime(remainingSeconds))
                sendBroadcast(Intent("TIME_UPDATE").apply {
                    setPackage(packageName)
                    putExtra("SECONDS", remainingSeconds)
                    putExtra("TOTAL_SECONDS", totalDurationSeconds)
                    putExtra("BYPASS_COUNT", bypassCount)
                })
            }
            completeSession()
        }

        monitorJob = serviceScope.launch {
            while (true) {
                delay(1000)
                if (!isBypassActive) {
                    checkAndBlockApps()
                } else if (System.currentTimeMillis() > bypassEndTime) {
                    isBypassActive = false
                }
            }
        }
    }

    private fun checkAndBlockApps() {
        val currentApp = UsageHelper.getForegroundApp(this)
        if (currentApp != null && distractingApps.contains(currentApp)) {
            val intent = Intent(this, OverlayActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun activateBypass(minutes: Int) {
        if (bypassCount > 0) {
            isBypassActive = true
            bypassEndTime = System.currentTimeMillis() + (minutes * 60 * 1000)
            bypassCount--
            sendBroadcast(Intent("SESSION_BYPASS_ACTIVATED").setPackage(packageName))
            // Update UI immediately with new count
            sendBroadcast(Intent("TIME_UPDATE").apply {
                setPackage(packageName)
                putExtra("SECONDS", remainingSeconds)
                putExtra("TOTAL_SECONDS", totalDurationSeconds)
                putExtra("BYPASS_COUNT", bypassCount)
            })
        }
    }

    private suspend fun completeSession() {
        val session = StudySession(
            startTime = System.currentTimeMillis() - (totalDurationSeconds * 1000),
            endTime = System.currentTimeMillis(),
            durationMinutes = totalDurationSeconds / 60,
            isCompleted = true
        )
        AppDatabase.getDatabase(this).sessionDao().insert(session)
        stopFocusSession()
    }

    private fun stopFocusSession() {
        // Restore Settings
        val notificationManager = getSystemService(NotificationManager::class.java)
        val audioManager = getSystemService(AudioManager::class.java)

        if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            originalPolicy?.let { notificationManager.notificationPolicy = it }
            audioManager.ringerMode = originalRingerMode
            Toast.makeText(this, "Silencer: Normal Mode Restored", Toast.LENGTH_SHORT).show()
        }

        timerJob?.cancel()
        monitorJob?.cancel()
        sendBroadcast(Intent("TIME_UPDATE").apply {
            setPackage(packageName)
            putExtra("SECONDS", 0L)
        })
        try { unregisterReceiver(blockListReceiver) } catch (e: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(title: String, content: String): Notification {
        val channelId = "focus_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Focus Mode", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification("Focus Session Active", content))
    }

    private fun broadcastStatus() {
        sendBroadcast(Intent("TIME_UPDATE").apply {
            setPackage(packageName)
            putExtra("SECONDS", if (timerJob?.isActive == true) remainingSeconds else 0L)
            putExtra("TOTAL_SECONDS", totalDurationSeconds)
            putExtra("BYPASS_COUNT", bypassCount)
        })
    }

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { serviceScope.cancel(); super.onDestroy() }
}
