package com.sergioasenjo.vesperhome.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "category_apps",
    primaryKeys = ["category_id", "component_name"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["category_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("component_name")]
)
data class CategoryAppEntity(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "component_name")
    val componentName: String,
    val position: Long
)
