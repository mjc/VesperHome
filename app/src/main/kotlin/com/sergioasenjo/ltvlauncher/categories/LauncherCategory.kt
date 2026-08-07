package com.sergioasenjo.ltvlauncher.categories

import com.sergioasenjo.ltvlauncher.applications.LauncherApp

data class LauncherCategory(val id: Long, val name: String, val apps: List<LauncherApp>)
