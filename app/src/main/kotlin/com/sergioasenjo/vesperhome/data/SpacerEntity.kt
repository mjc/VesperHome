package com.sergioasenjo.vesperhome.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spacers")
data class SpacerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "spacer_id")
    val spacerId: Long = 0,
    val position: Long,
    @ColumnInfo(defaultValue = "40")
    val height: Int = 40
)
