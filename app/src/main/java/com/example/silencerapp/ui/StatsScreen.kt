package com.example.silencerapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.silencerapp.data.AppDatabase
import com.example.silencerapp.data.StudySession
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val sessions by db.sessionDao().getAllSessions().collectAsState(initial = emptyList())
    val totalTime by db.sessionDao().getTotalStudyTime().collectAsState(initial = 0L)
    val sessionCount by db.sessionDao().getSessionCount().collectAsState(initial = 0)

    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val weekStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val todayTime by db.sessionDao().getStudyTimeSince(todayStart).collectAsState(initial = 0L)
    val todaySessions by db.sessionDao().getSessionCountSince(todayStart).collectAsState(initial = 0)
    
    val weeklyTime by db.sessionDao().getStudyTimeSince(weekStart).collectAsState(initial = 0L)
    val weeklySessions by db.sessionDao().getSessionCountSince(weekStart).collectAsState(initial = 0)

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        item {
            Text("Study Status", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("YOUR PROGRESS", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard("TOTAL TIME", "${totalTime ?: 0}m", "0 total", Icons.Default.AccessTime, Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(16.dp))
                    StatCard("SESSIONS", "$sessionCount", "all time", Icons.Default.Adjust, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard("TODAY", "${todayTime ?: 0}m", "$todaySessions sessions", Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(16.dp))
                    StatCard("THIS WEEK", "${weeklyTime ?: 0}m", "$weeklySessions sessions", Icons.Default.CalendarMonth, Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().height(250.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("WEEKLY OVERVIEW (MINUTES)", fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day -> Text(day, fontSize = 10.sp, color = Color.Gray) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (sessions.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No sessions yet. Start your first study session! 🎯", color = Color.Gray, fontSize = 14.sp) } }
        } else {
            items(sessions) { session -> SessionItem(session); HorizontalDivider(color = Color.White.copy(alpha = 0.05f)) }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, subLabel: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subLabel, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SessionItem(session: StudySession) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(sdf.format(Date(session.startTime)), fontSize = 14.sp, color = Color.White)
            Text(if (session.isCompleted) "Completed" else "Interrupted", fontSize = 12.sp, color = if (session.isCompleted) Color(0xFF00E5FF) else Color(0xFFFF5252))
        }
        Text("${session.durationMinutes} min", fontWeight = FontWeight.Bold, color = Color.White)
    }
}
