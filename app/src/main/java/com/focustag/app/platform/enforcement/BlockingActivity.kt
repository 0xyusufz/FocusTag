package com.focustag.app.platform.enforcement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focustag.app.MainActivity
import com.focustag.app.ui.theme.FocusTagTheme
import java.lang.ref.WeakReference

class BlockingActivity : ComponentActivity() {

    private var blockedPackageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        visibleInstance = WeakReference(this)
        blockedPackageName = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    returnToFocusTag()
                }
            }
        )

        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedPackageName = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        render()
    }

    override fun onDestroy() {
        if (visibleInstance?.get() === this) {
            visibleInstance = null
        }
        super.onDestroy()
    }

    private fun render() {
        setContent {
            FocusTagTheme {
                BlockingScreen(
                    blockedPackageName = blockedPackageName,
                    onReturnToFocusTag = ::returnToFocusTag
                )
            }
        }
    }

    private fun returnToFocusTag() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        private const val EXTRA_BLOCKED_PACKAGE = "blocked_package"

        private var visibleInstance: WeakReference<BlockingActivity>? = null

        val isVisible: Boolean
            get() = visibleInstance?.get() != null

        fun createIntent(context: Context, packageName: String): Intent {
            return Intent(context, BlockingActivity::class.java).apply {
                putExtra(EXTRA_BLOCKED_PACKAGE, packageName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        }

        fun finishVisibleInstance() {
            visibleInstance?.get()?.finish()
            visibleInstance = null
        }
    }
}

@Composable
private fun BlockingScreen(
    blockedPackageName: String,
    onReturnToFocusTag: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Focus Mode is active",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Access to this application is restricted right now.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = blockedPackageName.ifBlank { "Unknown application" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onReturnToFocusTag,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Return to FocusTag")
            }
        }
    }
}
