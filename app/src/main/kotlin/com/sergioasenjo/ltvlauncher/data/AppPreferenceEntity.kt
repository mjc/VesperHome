package com.sergioasenjo.ltvlauncher.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "component_name")
    val componentName: String,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,
    @ColumnInfo(name = "manual_order")
    val manualOrder: Long? = null,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "custom_banner_revision")
    val customBannerRevision: Long? = null
)
