package com.reeltracker.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reeltracker.domain.model.Platform

/**
 * Home screen showing today's confirmed Reels and Shorts counts.
 *
 * Uses [HomeViewModel] to observe a live [kotlinx.coroutines.flow.StateFlow] backed
 * by Room so counts update immediately as new events are inserted.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val counts by viewModel.todayCounts.collectAsStateWithLifecycle()

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text  = "Today",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PlatformCountCard(
                platform = Platform.INSTAGRAM,
                count    = counts[Platform.INSTAGRAM] ?: 0,
            )
            PlatformCountCard(
                platform = Platform.YOUTUBE,
                count    = counts[Platform.YOUTUBE] ?: 0,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text  = "Enable the accessibility service to start counting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlatformCountCard(
    platform: Platform,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier  = modifier.size(width = 150.dp, height = 120.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = platform.displayName,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text       = count.toString(),
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
