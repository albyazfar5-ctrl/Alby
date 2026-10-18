package com.example.simplelauncher

import android.graphics.drawable.Drawable

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val folderId: String? = null,
    val folderApps: List<AppItem> = emptyList()
) {
    val isFolder: Boolean get() = folderId != null
}
