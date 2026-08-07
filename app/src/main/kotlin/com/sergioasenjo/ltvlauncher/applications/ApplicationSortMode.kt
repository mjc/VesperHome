package com.sergioasenjo.ltvlauncher.applications

enum class ApplicationSortMode {
    MANUAL,
    ALPHABETICAL,
    LAST_USED
}

fun List<LauncherApp>.sortedForDisplay(sortMode: ApplicationSortMode): List<LauncherApp> = when (sortMode) {
    ApplicationSortMode.MANUAL -> sortedWith(
        compareBy<LauncherApp> { it.manualOrder ?: Long.MAX_VALUE }
            .thenBy { it.label.lowercase() }
    )

    ApplicationSortMode.ALPHABETICAL -> sortedBy { it.label.lowercase() }

    ApplicationSortMode.LAST_USED -> sortedWith(
        compareByDescending<LauncherApp> { it.lastUsedAt ?: Long.MIN_VALUE }
            .thenBy { it.label.lowercase() }
    )
}
