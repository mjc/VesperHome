package com.sergioasenjo.vesperhome.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "category_id")
    val categoryId: Long = 0,
    val name: String,
    val position: Long,
    @ColumnInfo(name = "sort_mode", defaultValue = "'MANUAL'")
    val sortMode: String = "MANUAL",
    @ColumnInfo(name = "layout_type", defaultValue = "'ROW'")
    val layoutType: String = "ROW",
    @ColumnInfo(name = "grid_columns", defaultValue = "6")
    val gridColumns: Int = 6,
    @ColumnInfo(name = "row_height", defaultValue = "110")
    val rowHeight: Int = 110
)
