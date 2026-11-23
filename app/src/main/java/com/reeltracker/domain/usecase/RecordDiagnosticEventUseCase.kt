package com.reeltracker.domain.usecase

import com.reeltracker.data.db.DiagnosticEventEntity
import com.reeltracker.data.repository.DiagnosticEventRepository
import com.reeltracker.domain.model.Platform

/**
 * Records a diagnostic event when detection status is UNKNOWN_SCREEN or otherwise
 * noteworthy. Contains only structural metadata — no text from monitored apps.
 *
 * @param unconfirmedDiscards Session-cumulative count of unconfirmed-and-discarded
 *   transition candidates at the time of this diagnostic event.
 */
class RecordDiagnosticEventUseCase(private val repository: DiagnosticEventRepository) {

    suspend operator fun invoke(
        platform: Platform,
        timestampMs: Long,
        eventType: Int,
        screenState: String,
        confidenceScore: Float,
        signalBitmask: Int,
        unconfirmedDiscards: Int,
    ) {
        val entity = DiagnosticEventEntity(
            timestampMs = timestampMs,
            platform = platform.name,
            eventType = eventType,
            screenState = screenState,
            confidenceScore = confidenceScore,
            signalBitmask = signalBitmask,
            unconfirmedDiscards = unconfirmedDiscards,
        )
        repository.insert(entity)
    }
}
