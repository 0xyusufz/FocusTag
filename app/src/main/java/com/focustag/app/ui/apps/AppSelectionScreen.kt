package com.focustag.app.ui.apps

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustag.app.data.model.AppCategory
import com.focustag.app.data.model.FocusAction
import com.focustag.app.data.model.ResolvedPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(viewModel: AppSelectionViewModel, isFocusActive: Boolean, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Focus Apps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        PolicyLegend()
                    }
                    
                    val grouped = state.resolvedPolicies.groupBy { it.appInfo.category }
                    
                    AppCategory.entries.forEach { category ->
                        val appsInCategory = grouped[category] ?: emptyList()
                        if (appsInCategory.isNotEmpty()) {
                            item {
                                CategoryHeader(category.name)
                            }
                            items(appsInCategory) { resolvedPolicy ->
                                AppPolicyItem(
                                    resolvedPolicy = resolvedPolicy,
                                    isFocusActive = isFocusActive,
                                    onToggle = { viewModel.toggleAppSelection(it) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PolicyLegend() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Rules for Focus Mode:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        LegendItem("L", "PROTECTED: Always allowed (e.g. FocusTag)")
        LegendItem("X", "RESTRICTED: Always blocked (e.g. Instagram)")
        LegendItem("V", "SELECTED: You chose to block this")
    }
}

@Composable
fun LegendItem(symbol: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(symbol, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CategoryHeader(name: String) {
    Text(
        text = name,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun AppPolicyItem(resolvedPolicy: ResolvedPolicy, isFocusActive: Boolean, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = resolvedPolicy.appInfo.appName, style = MaterialTheme.typography.bodyLarge)
            Text(text = resolvedPolicy.appInfo.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }

        when (resolvedPolicy.action) {
            FocusAction.PROTECTED -> {
                Text("L", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            FocusAction.BLOCK -> {
                if (resolvedPolicy.appInfo.category == AppCategory.RESTRICTED) {
                    Text("X", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                } else {
                    Checkbox(
                        checked = true,
                        onCheckedChange = { onToggle(resolvedPolicy.appInfo.packageName) },
                        enabled = !isFocusActive
                    )
                }
            }
            FocusAction.ALLOW -> {
                Checkbox(
                    checked = false,
                    onCheckedChange = { onToggle(resolvedPolicy.appInfo.packageName) },
                    enabled = !isFocusActive
                )
            }
        }
    }
}
