package com.example.silencerapp

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import java.util.*

object UsageHelper {
    private const val PREFS_NAME = "silencer_prefs"
    private const val BLOCKED_APPS_KEY = "blocked_packages"

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        val usageStats = getUsageStats(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val manuallyUnblocked = prefs.getStringSet("unblocked", emptySet()) ?: emptySet()
        val manuallyBlocked = prefs.getStringSet("blocked", emptySet()) ?: emptySet()

        return apps.filter { 
            (pm.getLaunchIntentForPackage(it.packageName) != null) && 
            it.packageName != context.packageName
        }.map { app ->
            val stats = usageStats[app.packageName]
            val usageMinutes = (stats?.totalTimeInForeground ?: 0L) / 60000
            
            // Logic: Block if manually blocked OR (Usage > 10m AND not manually unblocked)
            val isBlocked = manuallyBlocked.contains(app.packageName) || 
                           (usageMinutes >= 10 && !manuallyUnblocked.contains(app.packageName))

            AppInfo(
                packageName = app.packageName,
                name = pm.getApplicationLabel(app).toString(),
                usageMinutes = usageMinutes,
                isBlocked = isBlocked
            )
        }.sortedByDescending { it.usageMinutes }
    }

    fun setAppBlocked(context: Context, packageName: String, blocked: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unblocked = prefs.getStringSet("unblocked", emptySet())?.toMutableSet() ?: mutableSetOf()
        val blockedSet = prefs.getStringSet("blocked", emptySet())?.toMutableSet() ?: mutableSetOf()

        if (blocked) {
            unblocked.remove(packageName)
            blockedSet.add(packageName)
        } else {
            blockedSet.remove(packageName)
            unblocked.add(packageName)
        }

        prefs.edit()
            .putStringSet("unblocked", unblocked)
            .putStringSet("blocked", blockedSet)
            .apply()
            
        // Notify service if running
        context.sendBroadcast(Intent("BLOCK_LIST_UPDATED"))
    }

    fun getBlockedPackages(context: Context): List<String> {
        return getInstalledApps(context).filter { it.isBlocked }.map { it.packageName }
    }

    private fun getUsageStats(context: Context): Map<String, android.app.usage.UsageStats> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        return usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
    }

    fun getForegroundApp(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 5 // Last 5 seconds

        val stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, startTime, endTime)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}

data class AppInfo(
    val packageName: String, 
    val name: String, 
    val usageMinutes: Long,
    val isBlocked: Boolean = false
)
