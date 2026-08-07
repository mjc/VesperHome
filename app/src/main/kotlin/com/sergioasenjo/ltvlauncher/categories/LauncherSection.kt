package com.sergioasenjo.ltvlauncher.categories

import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.applications.LauncherApp

enum class CategoryLayoutType {
    ROW,
    GRID
}

enum class LauncherSectionKind {
    CATEGORY,
    SPACER
}

interface LauncherSection {
    val stableId: Long
    val position: Long
    val kind: LauncherSectionKind
}

data class LauncherCategory(
    val id: Long,
    val name: String,
    override val position: Long,
    val sortMode: ApplicationSortMode,
    val layoutType: CategoryLayoutType,
    val gridColumns: Int,
    val rowHeight: Int,
    val apps: List<LauncherApp>
) : LauncherSection {
    override val stableId: Long = id
    override val kind: LauncherSectionKind = LauncherSectionKind.CATEGORY
}

data class LauncherSpacer(val id: Long, override val position: Long, val height: Int) : LauncherSection {
    override val stableId: Long = Long.MIN_VALUE + id
    override val kind: LauncherSectionKind = LauncherSectionKind.SPACER
}
