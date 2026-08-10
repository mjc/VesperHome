package com.sergioasenjo.ltvlauncher.upcoming

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sergioasenjo.ltvlauncher.databinding.ViewCalendarServiceBinding

class MediaServiceFormView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val binding = ViewCalendarServiceBinding.inflate(LayoutInflater.from(context), this, true)

    val url: String
        get() = binding.serverUrl.text.toString()

    val apiKey: String
        get() = binding.apiKey.text.toString()

    fun configure(title: String, description: String) {
        binding.title.text = title
        binding.description.text = description
    }

    fun setValues(url: String, apiKey: String) {
        binding.serverUrl.setText(url)
        binding.apiKey.setText(apiKey)
    }

    fun setOnSaveClick(listener: () -> Unit) {
        binding.save.setOnClickListener { listener() }
    }

    fun render(saving: Boolean, status: String) {
        binding.save.isEnabled = !saving
        binding.progress.isVisible = saving
        binding.status.text = status
        binding.status.isVisible = status.isNotEmpty()
    }
}
