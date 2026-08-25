package com.example.silencerapp.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.silencerapp.AppInfo
import com.example.silencerapp.UsageHelper

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    
    fun refreshApps() {
        apps = UsageHelper.getInstalledApps(context)
    }

    LaunchedEffect(Unit) {
        refreshApps()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Managed Apps", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("USAGE ANALYSIS", fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = { refreshApps() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(), 
            colors = CardDefaults.cardColors(containerColor = Color(0xFF00BFA5).copy(alpha = 0.1f)), 
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF00BFA5).copy(alpha = 0.2f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("${apps.size} apps detected", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                    Text("Apps with >10m usage are auto-blocked", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn {
            items(apps) { app ->
                AppItem(
                    app = app,
                    onToggleBlock = { isBlocked ->
                        UsageHelper.setAppBlocked(context, app.packageName, isBlocked)
                        refreshApps()
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun AppItem(app: AppInfo, onToggleBlock: (Boolean) -> Unit) {
    val context = LocalContext.current
    val pm = remember { context.packageManager }
    val appIcon = remember(app.packageName) {
        try {
            pm.getApplicationIcon(app.packageName)
        } catch (e: Exception) { null }
    }

    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)), 
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(), 
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (appIcon is BitmapDrawable) {
                    Image(
                        painter = BitmapPainter(appIcon.bitmap.asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFF252525), RoundedCornerShape(8.dp)))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(app.name, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                    Text("${app.usageMinutes}m used today", fontSize = 12.sp, color = Color.Gray)
                }
            }
            
            Switch(
                checked = app.isBlocked,
                onCheckedChange = { onToggleBlock(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00E5FF),
                    checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF252525)
                )
            )
        }
    }
}
