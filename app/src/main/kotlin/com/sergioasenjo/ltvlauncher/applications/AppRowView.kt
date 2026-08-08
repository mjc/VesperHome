package com.sergioasenjo.ltvlauncher.applications

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.sergioasenjo.ltvlauncher.databinding.ViewAppRowBinding

class AppRowView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {
    private val binding = ViewAppRowBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        clipChildren = false
        clipToPadding = false
    }

    val categoryHeader = binding.categoryHeader
    val accentTick = binding.accentTick
    val title = binding.title
    val appCount = binding.appCount
    val apps = binding.apps
}
