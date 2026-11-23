package com.reeltracker.domain.model

enum class Platform(val packageName: String, val displayName: String) {
    INSTAGRAM("com.instagram.android", "Instagram"),
    YOUTUBE("com.google.android.youtube", "YouTube");

    companion object {
        fun fromPackage(pkg: String): Platform =
            values().firstOrNull { it.packageName == pkg }
                ?: throw IllegalArgumentException("Unknown package: $pkg")

        fun fromString(value: String): Platform = valueOf(value)
    }
}
