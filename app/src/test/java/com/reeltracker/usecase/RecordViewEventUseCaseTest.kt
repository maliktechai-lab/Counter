package com.reeltracker.usecase

import com.reeltracker.data.repository.ViewEventRepository
import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.model.TransitionType
import com.reeltracker.domain.model.ViewEvent
import com.reeltracker.domain.usecase.RecordViewEventUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RecordViewEventUseCaseTest {

    private val repository: ViewEventRepository = mockk(relaxed = true)
    private val useCase = RecordViewEventUseCase(repository)

    @Test
    fun `invoke passes event to repository insert`() = runTest {
        val event = ViewEvent(
            platform = Platform.INSTAGRAM,
            timestampMs = 1_700_000_000_000L,
            transitionType = TransitionType.ENTRY,
            confidence = 0.80f,
        )
        coEvery { repository.insert(event) } returns Unit

        useCase(event)

        coVerify(exactly = 1) { repository.insert(event) }
    }

    @Test
    fun `invoke passes TRANSITION event to repository`() = runTest {
        val event = ViewEvent(
            platform = Platform.YOUTUBE,
            timestampMs = 1_700_000_001_000L,
            transitionType = TransitionType.TRANSITION,
            confidence = 0.70f,
        )
        coEvery { repository.insert(event) } returns Unit

        useCase(event)

        coVerify(exactly = 1) { repository.insert(event) }
    }
}
