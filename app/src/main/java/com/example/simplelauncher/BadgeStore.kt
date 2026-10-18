package com.example.simplelauncher

object BadgeStore {
    @Volatile var counts: Map<String, Int> = emptyMap()
    @Volatile var enabled: Boolean = false
    var onChange: (() -> Unit)? = null
}
