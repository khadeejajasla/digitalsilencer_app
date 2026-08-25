package com.example.silencerapp

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.compose.*
import com.example.silencerapp.ui.MainScreen
import com.example.silencerapp.ui.StatsScreen
import com.example.silencerapp.ui.AppsScreen

class MainActivity : ComponentActivity() {
    private var remainingTime = mutableLongStateOf(0L)
    private var totalTime = mutableLongStateOf(0L)
    private var bypassCount = mutableIntStateOf(2)

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            remainingTime.longValue = intent?.getLongExtra("SECONDS", 0L) ?: 0L
            totalTime.longValue = intent?.getLongExtra("TOTAL_SECONDS", 0L) ?: 0L
            bypassCount.intValue = intent?.getIntExtra("BYPASS_COUNT", 2) ?: 2
            
            if (remainingTime.longValue > 0) {
                // Toast.makeText(context, "Session sync: ${remainingTime.longValue}s left", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        Handler(Looper.getMainLooper()).postDelayed({
            keepSplashScreen = false
        }, 1000)

        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            SilencerTheme {
                val navController = rememberNavController()
                val items = listOf(
                    NavItem("Focus", "main", Icons.Outlined.Timer, Icons.Filled.Timer),
                    NavItem("Status", "stats", Icons.Outlined.BarChart, Icons.Filled.BarChart),
                    NavItem("Apps", "apps", Icons.Outlined.Apps, Icons.Outlined.Apps)
                )

                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route
                            items.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navController.navigate(item.route) { popUpTo("main"); launchSingleTop = true } },
                                    icon = { Icon(if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF00E5FF),
                                        selectedTextColor = Color(0xFF00E5FF),
                                        indicatorColor = Color.Transparent,
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    Surface(modifier = Modifier.padding(padding).fillMaxSize(), color = Color(0xFF0D0D0D)) {
                        NavHost(navController = navController, startDestination = "main") {
                            composable("main") { MainScreen(remainingTime.longValue, totalTime.longValue, bypassCount.intValue) }
                            composable("stats") { StatsScreen() }
                            composable("apps") { AppsScreen() }
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter("TIME_UPDATE")
        ContextCompat.registerReceiver(
            this,
            timeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        // Request current status if service is already running
        startService(Intent(this, FocusService::class.java).apply { action = "GET_STATUS" })
    }

    override fun onResume() {
        super.onResume()
        startService(Intent(this, FocusService::class.java).apply { action = "GET_STATUS" })
    }

    private fun checkPermissions() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "Silencer: DND Access Required", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        if (!hasUsageStats()) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            startActivity(intent)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun hasUsageStats(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onDestroy() { 
        try {
            unregisterReceiver(timeReceiver)
        } catch (_: Exception) {}
        super.onDestroy() 
    }
}

data class NavItem(val label: String, val route: String, val unselectedIcon: ImageVector, val selectedIcon: ImageVector)

@Composable
fun SilencerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00E5FF),
            background = Color(0xFF0D0D0D),
            surface = Color(0xFF1E1E1E)
        ),
        content = content
    )
}
