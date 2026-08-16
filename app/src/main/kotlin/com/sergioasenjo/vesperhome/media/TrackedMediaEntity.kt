package com.sergioasenjo.vesperhome.media

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "tracked_media",
    primaryKeys = ["provider", "external_id"]
)
data class TrackedMediaEntity(
    val provider: String,
    @ColumnInfo(name = "external_id")
    val externalId: Int,
    @ColumnInfo(name = "provider_item_id")
    val providerItemId: Int?,
    val title: String,
    val year: Int,
    @ColumnInfo(name = "poster_url")
    val posterUrl: String?,
    val state: String,
    val detail: String,
    val progress: Int?,
    @ColumnInfo(name = "added_at")
    val addedAt: Long
)

fun TrackedMediaEntity.toModel(): TrackedMedia = TrackedMedia(
    provider = MediaProvider.valueOf(provider),
    externalId = externalId,
    providerItemId = providerItemId,
    title = title,
    year = year,
    posterUrl = posterUrl,
    state = TrackedMediaState.valueOf(state),
    detail = detail,
    progress = progress,
    addedAt = addedAt
)

fun TrackedMedia.toEntity(): TrackedMediaEntity = TrackedMediaEntity(
    provider = provider.name,
    externalId = externalId,
    providerItemId = providerItemId,
    title = title,
    year = year,
    posterUrl = posterUrl,
    state = state.name,
    detail = detail,
    progress = progress,
    addedAt = addedAt
)
