package com.focustag.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustag.app.data.model.FocusState
import com.focustag.app.ui.focus.FocusViewModel
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    focusViewModel: FocusViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToApps: () -> Unit
) {
    val sessionStatus by authViewModel.sessionStatus.collectAsState()
    val focusSessionState by focusViewModel.focusState.collectAsState()
    val enforcementStatus by focusViewModel.enforcementStatus.collectAsState()
    val isTransitioning by focusViewModel.isTransitioning.collectAsState()
    
    val userEmail = when (val status = sessionStatus) {
        is SessionStatus.Authenticated -> status.session.user?.email ?: "Unknown User"
        else -> "Guest"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // Logo Placeholder (Simple Box instead of Icon to avoid extra dependencies)
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "FT",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Text(
            text = "FocusTag",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Welcome to FocusTag",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Text(
            text = userEmail,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Focus Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Focus Status Display
                val isFocusActive = focusSessionState.focusState == FocusState.FOCUS_ACTIVE
                Text(
                    text = if (isFocusActive) "STATUS: ACTIVE" else "STATUS: OFF",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocusActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
                
                if (isFocusActive) {
                    Text(
                        text = "Enforcement: ${enforcementStatus.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = focusViewModel::onSimulatedTagTap,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTransitioning,
                    colors = if (isFocusActive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) {
                    if (isTransitioning) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (isFocusActive) "Simulate Focus Tag (Deactivate)" else "Simulate Focus Tag (Activate)")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onNavigateToApps,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isFocusActive
                ) {
                    Text("Manage Focus Apps")
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isFocusActive) {
                    Text(
                        text = "Configuration locked while Focus is active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "NFC Attendance functionality coming soon. Stay tuned!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        val isFocusActive = focusSessionState.focusState == FocusState.FOCUS_ACTIVE

        OutlinedButton(
            onClick = onNavigateToProfile,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isFocusActive
        ) {
            Text("View Profile")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = authViewModel::signOut,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isFocusActive
        ) {
            Text("Log Out")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}
