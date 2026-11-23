package com.reeltracker.domain.model

/** Aggregated confirmed count for one platform on one calendar date. */
data class DailyCount(
    /** ISO-8601 date string "YYYY-MM-DD". */
    val date: String,
    val platform: Platform,
    val count: Int,
)
