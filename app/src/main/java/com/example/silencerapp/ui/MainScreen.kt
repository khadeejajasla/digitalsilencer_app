package com.example.silencerapp.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.silencerapp.FocusService

@Composable
fun MainScreen(remainingSeconds: Long, totalSeconds: Long, bypassCount: Int) {
    val context = LocalContext.current
    var selectedHours by remember { mutableIntStateOf(0) }
    var selectedMinutes by remember { mutableIntStateOf(30) }
    val scrollState = rememberScrollState()
    var showBypassDialog by remember { mutableStateOf(false) }

    if (showBypassDialog) {
        EmergencyBypassDialog(
            remainingBypasses = bypassCount,
            onDismiss = { showBypassDialog = false },
            onBypassSelected = { minutes ->
                context.startService(Intent(context, FocusService::class.java).apply { 
                    action = "BYPASS"
                    putExtra("BYPASS_MINUTES", minutes)
                })
                showBypassDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("DIGITAL SILENCER", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
        Text("STUDY FOCUS", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(48.dp))

        // Timer Circle
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
            // Glow effect
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (remainingSeconds > 0) {
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.05f),
                        radius = size.minDimension / 2 + 10.dp.toPx()
                    )
                }
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background track
                drawArc(color = Color(0xFF1A1A1A), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 8.dp.toPx()))
                
                // Outer Cyan Line (New)
                drawArc(
                    color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 1.dp.toPx())
                )

                if (remainingSeconds > 0) {
                    val progress = (remainingSeconds.toFloat() / (totalSeconds.toFloat().coerceAtLeast(1f)))
                    drawArc(
                        color = Color(0xFF00E5FF), 
                        startAngle = -90f, 
                        sweepAngle = progress * 360f, 
                        useCenter = false, 
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = formatTime(if (remainingSeconds > 0) remainingSeconds else (selectedHours * 3600 + selectedMinutes * 60).toLong()), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = if (remainingSeconds > 0) "FOCUSING" else "READY", fontSize = 14.sp, color = Color.Gray, letterSpacing = 2.sp)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (remainingSeconds == 0L) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Adjuster(value = selectedHours, label = "HOURS", onValueChange = { selectedHours = it }, range = 0..12)
                Spacer(modifier = Modifier.width(32.dp))
                Text(":", fontSize = 32.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 24.dp))
                Spacer(modifier = Modifier.width(32.dp))
                Adjuster(value = selectedMinutes, label = "MINUTES", onValueChange = { selectedMinutes = it }, range = 0..59)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Preset("15m", selectedHours == 0 && selectedMinutes == 15) { selectedHours = 0; selectedMinutes = 15 }
                Preset("30m", selectedHours == 0 && selectedMinutes == 30) { selectedHours = 0; selectedMinutes = 30 }
                Preset("1h", selectedHours == 1 && selectedMinutes == 0) { selectedHours = 1; selectedMinutes = 0 }
                Preset("2h", selectedHours == 2 && selectedMinutes == 0) { selectedHours = 2; selectedMinutes = 0 }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    val h = selectedHours.toLong()
                    val m = selectedMinutes.toLong()
                    val totalMinutes = h * 60 + m
                    
                    if (totalMinutes > 0) {
                        try {
                            android.widget.Toast.makeText(context, "Requesting Session: ${totalMinutes}m", android.widget.Toast.LENGTH_SHORT).show()
                            val intent = Intent(context, FocusService::class.java).apply { 
                                action = "START"
                                putExtra("DURATION_MINUTES", totalMinutes) 
                            }
                            context.startForegroundService(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Please select at least 1 minute", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Study Session", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { context.startService(Intent(context, FocusService::class.java).apply { action = "STOP" }) }, 
                modifier = Modifier.fillMaxWidth().height(64.dp), 
                shape = RoundedCornerShape(16.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935).copy(alpha = 0.1f), contentColor = Color(0xFFE53935)),
                border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Session", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showBypassDialog = true },
                    modifier = Modifier.weight(1f).height(56.dp), 
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB300)),
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFB300))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Emergency Bypass", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { 
                        val intent = Intent(context, OverlayActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }, 
                    modifier = Modifier.weight(1f).height(56.dp), 
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Block", fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun EmergencyBypassDialog(remainingBypasses: Int, onDismiss: () -> Unit, onBypassSelected: (Int) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Emergency Bypass", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = buildAnnotatedString {
                        append("Temporarily unlock blocked apps. You have ")
                        withStyle(SpanStyle(color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)) {
                            append(remainingBypasses.toString())
                        }
                        append(" bypasses remaining this session.")
                    },
                    color = Color.Gray,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onBypassSelected(5) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E), contentColor = Color(0xFFFFB300)),
                        border = BorderStroke(width = 1.dp, color = Color(0xFFFFB300).copy(alpha = 0.3f))
                    ) {
                        Text("5 minutes", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onBypassSelected(10) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E), contentColor = Color(0xFFFFB300)),
                        border = BorderStroke(width = 1.dp, color = Color(0xFFFFB300).copy(alpha = 0.3f))
                    ) {
                        Text("10 minutes", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun Adjuster(value: Int, label: String, onValueChange: (Int) -> Unit, range: IntRange) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(40.dp).background(Color(0xFF1E1E1E), CircleShape).clickable { if (value < range.last) onValueChange(value + 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(text = value.toString(), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        Box(modifier = Modifier.size(40.dp).background(Color(0xFF1E1E1E), CircleShape).clickable { if (value > range.first) onValueChange(value - 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun Preset(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.clickable { onClick() }, shape = RoundedCornerShape(20.dp), color = if (selected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF1A1A1A)) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text = label, color = if (selected) Color(0xFF00E5FF) else Color.Gray, fontSize = 14.sp)
        }
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
