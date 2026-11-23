package com.reeltracker.domain.usecase

import com.reeltracker.data.repository.ViewEventRepository
import com.reeltracker.domain.model.ViewEvent

/**
 * Persists a confirmed [ViewEvent] to the local database.
 *
 * Only confirmed events (those with corroborating evidence of video advancement)
 * reach this use case. Unconfirmed candidates are discarded by the
 * TransitionConfirmationBuffer and never reach here.
 */
class RecordViewEventUseCase(private val repository: ViewEventRepository) {
    suspend operator fun invoke(event: ViewEvent) {
        repository.insert(event)
    }
}
