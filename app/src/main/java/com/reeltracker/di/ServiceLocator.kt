package com.reeltracker.di

import android.content.Context
import com.reeltracker.data.db.AppDatabase
import com.reeltracker.data.repository.DiagnosticEventRepository
import com.reeltracker.data.repository.ViewEventRepository
import com.reeltracker.domain.usecase.GetStatsUseCase
import com.reeltracker.domain.usecase.RecordDiagnosticEventUseCase
import com.reeltracker.domain.usecase.RecordViewEventUseCase

/**
 * Manual service locator (no Hilt/Dagger dependency for V1 simplicity).
 * Initialised once in [com.reeltracker.ReelTrackerApp.onCreate].
 */
object ServiceLocator {

    private lateinit var appContext: Context

    // ── Database ──────────────────────────────────────────────────────────────

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    // ── Repositories ──────────────────────────────────────────────────────────

    val viewEventRepository: ViewEventRepository by lazy {
        ViewEventRepository(database.viewEventDao())
    }

    val diagnosticEventRepository: DiagnosticEventRepository by lazy {
        DiagnosticEventRepository(database.diagnosticEventDao())
    }

    // ── Use cases ──────────────────────────────────────────────────────────────

    val recordViewEventUseCase: RecordViewEventUseCase by lazy {
        RecordViewEventUseCase(viewEventRepository)
    }

    val recordDiagnosticEventUseCase: RecordDiagnosticEventUseCase by lazy {
        RecordDiagnosticEventUseCase(diagnosticEventRepository)
    }

    val getStatsUseCase: GetStatsUseCase by lazy {
        GetStatsUseCase(viewEventRepository)
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
