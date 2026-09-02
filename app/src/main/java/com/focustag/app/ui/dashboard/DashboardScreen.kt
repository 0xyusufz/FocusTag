package com.focustag.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustag.app.data.model.SessionStatus
import com.focustag.app.ui.history.HistorySessionItem
import java.util.concurrent.TimeUnit

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToFocus: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            DashboardHeader(onProfileClick = onNavigateToProfile)
        }

        item {
            TodayStatsSummary(state)
        }

        if (state.activeSession != null) {
            item {
                ActiveSessionCard(state.activeSession!!, onNavigateToFocus)
            }
        } else {
            item {
                QuickStartCard(onNavigateToFocus)
            }
        }

        item {
            WeeklyChartCard(state.weeklyStats)
        }

        item {
            RecentActivityHeader(onViewAll = onNavigateToHistory)
        }

        if (state.recentSessions.isEmpty()) {
            item {
                Text(
                    text = "No recent sessions. Start your first focus session!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(state.recentSessions) { item ->
                RecentSessionRow(item)
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DashboardHeader(onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Track your focus journey",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        
        OutlinedButton(
            onClick = onProfileClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Profile")
        }
    }
}

@Composable
fun TodayStatsSummary(state: DashboardState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Focus",
            value = formatDuration(state.todayDurationMillis),
            icon = Icons.Default.Timer,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Sessions",
            value = state.todaySessionCount.toString(),
            icon = Icons.Default.Schedule,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Blocked",
            value = state.todayBlockedCount.toString(),
            icon = Icons.Default.Block,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun ActiveSessionCard(session: com.focustag.app.data.model.FocusSessionRecord, onNavigate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        onClick = onNavigate
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FOCUS ACTIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Session in progress...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Button(
                onClick = onNavigate,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Manage")
            }
        }
    }
}

@Composable
fun QuickStartCard(onNavigate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp),
        onClick = onNavigate
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ready to focus?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Start a new session using your NFC tag.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Focus Controls")
            }
        }
    }
}

@Composable
fun WeeklyChartCard(stats: List<WeeklyStat>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Weekly Focus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val maxDuration = stats.maxOfOrNull { it.durationMillis }?.coerceAtLeast(3600000L) ?: 3600000L
                stats.forEach { stat ->
                    val barHeightFraction = (stat.durationMillis.toFloat() / maxDuration).coerceIn(0.01f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .weight(barHeightFraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stat.dayName, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentActivityHeader(onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        OutlinedButton(onClick = onViewAll) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View All")
        }
    }
}

@Composable
fun RecentSessionRow(item: HistorySessionItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (item.record.status == SessionStatus.COMPLETED) "Completed Session" else "Interrupted Session",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm")
                        .withZone(java.time.ZoneId.systemDefault())
                        .format(java.time.Instant.ofEpochMilli(item.record.startAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = formatDuration(item.durationMillis ?: 0L),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
