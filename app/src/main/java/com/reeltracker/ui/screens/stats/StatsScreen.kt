package com.reeltracker.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reeltracker.domain.model.DailyCount
import com.reeltracker.domain.model.Platform

/**
 * Stats screen showing confirmed Reels and Shorts counts for a weekly or monthly period.
 *
 * The user can toggle between WEEK and MONTH views using filter chips.
 * Each day is listed with per-platform counts.
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier  = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text  = "Stats",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Period selector
        Row(horizontalArrangement = Arrangement.Start) {
            FilterChip(
                selected = state.period == StatsPeriod.WEEK,
                onClick  = { viewModel.selectPeriod(StatsPeriod.WEEK) },
                label    = { Text("This week") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = state.period == StatsPeriod.MONTH,
                onClick  = { viewModel.selectPeriod(StatsPeriod.MONTH) },
                label    = { Text("This month") },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.counts.isEmpty()) {
            Text(
                text  = "No data for this period yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StatsCountList(counts = state.counts)
        }
    }
}

@Composable
private fun StatsCountList(counts: List<DailyCount>) {
    // Group by date, then by platform
    val grouped = counts.groupBy { it.date }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(grouped.entries.toList(), key = { it.key }) { (date, dayCounts) ->
            DayRow(date = date, counts = dayCounts)
        }
    }
}

@Composable
private fun DayRow(date: String, counts: List<DailyCount>) {
    val igCount = counts.firstOrNull { it.platform == Platform.INSTAGRAM }?.count ?: 0
    val ytCount = counts.firstOrNull { it.platform == Platform.YOUTUBE }?.count ?: 0

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = date,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row {
            PlatformBadge(label = "IG",  count = igCount)
            Spacer(modifier = Modifier.width(12.dp))
            PlatformBadge(label = "YT",  count = ytCount)
        }
    }
}

@Composable
private fun PlatformBadge(label: String, count: Int) {
    Text(
        text  = "$label: $count",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
