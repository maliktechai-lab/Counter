package com.reeltracker.ui.screens.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Privacy screen: static explanation of what ReelTracker does and does not monitor.
 *
 * Content mirrors the README Privacy section and the accessibility service description string.
 */
@Composable
fun PrivacyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text  = "Privacy",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader("What ReelTracker reads")
        Body(
            "Only view layout identifiers (resource IDs) and scroll position numbers " +
            "from Instagram and YouTube. These are structural labels used to identify " +
            "the type of screen, not its content."
        )

        Spacer(modifier = Modifier.height(12.dp))

        SectionHeader("What ReelTracker never reads")
        Body(
            "Video titles, captions, account names, messages, search queries, or any " +
            "text visible on screen. The monitoring is strictly limited to view IDs " +
            "and scroll events."
        )

        Spacer(modifier = Modifier.height(12.dp))

        SectionHeader("Where data is stored")
        Body(
            "All data stays on your device in a local SQLite database. There is no " +
            "network permission — the app literally cannot send data anywhere. " +
            "Data is deleted when you uninstall the app."
        )

        Spacer(modifier = Modifier.height(12.dp))

        SectionHeader("Which apps are monitored")
        Body(
            "Only Instagram (com.instagram.android) and YouTube (com.google.android.youtube). " +
            "All other apps are invisible to the accessibility service."
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun Body(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.bodyMedium,
    )
}
