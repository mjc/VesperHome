package com.sergioasenjo.vesperhome.inputs

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

enum class TvInputType {
    HDMI,
    AV,
    TUNER,
    OTHER
}

data class TvInput(val id: String, val label: String, val type: TvInputType, val connected: Boolean)

class TvInputRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val inputManager = applicationContext.getSystemService(TvInputManager::class.java)

    fun observeInputs(): Flow<List<TvInput>> = callbackFlow {
        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputAdded(inputId: String) {
                trySend(Unit)
            }

            override fun onInputRemoved(inputId: String) {
                trySend(Unit)
            }

            override fun onInputStateChanged(inputId: String, state: Int) {
                trySend(Unit)
            }

            override fun onInputUpdated(inputId: String) {
                trySend(Unit)
            }

            override fun onTvInputInfoUpdated(inputInfo: TvInputInfo) {
                trySend(Unit)
            }
        }
        inputManager.registerCallback(callback, Handler(Looper.getMainLooper()))
        trySend(Unit)
        awaitClose { inputManager.unregisterCallback(callback) }
    }
        .buffer(Channel.CONFLATED)
        .map { loadInputs() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    fun createSwitchIntent(input: TvInput): Intent = Intent(
        Intent.ACTION_VIEW,
        TvContract.buildChannelUriForPassthroughInput(input.id)
    )

    private fun loadInputs(): List<TvInput> = inputManager.tvInputList
        .asSequence()
        .filter(TvInputInfo::isPassthroughInput)
        .filter { info -> Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !info.isHidden(applicationContext) }
        .map { info ->
            TvInput(
                id = info.id,
                label = info.displayLabel(),
                type = info.type.toInputType(),
                connected = inputManager.getInputState(info.id) != TvInputManager.INPUT_STATE_DISCONNECTED
            )
        }
        .sortedWith(compareBy(TvInput::type, TvInput::label))
        .toList()

    private fun TvInputInfo.displayLabel(): String {
        val customLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            loadCustomLabel(applicationContext)?.toString()
        } else {
            null
        }
        return customLabel?.takeIf(String::isNotBlank)
            ?: loadLabel(applicationContext)?.toString()?.takeIf(String::isNotBlank)
            ?: id
    }

    private fun Int.toInputType(): TvInputType = when (this) {
        TvInputInfo.TYPE_HDMI -> TvInputType.HDMI

        TvInputInfo.TYPE_COMPOSITE,
        TvInputInfo.TYPE_COMPONENT,
        TvInputInfo.TYPE_SVIDEO,
        TvInputInfo.TYPE_SCART -> TvInputType.AV

        TvInputInfo.TYPE_TUNER -> TvInputType.TUNER

        else -> TvInputType.OTHER
    }
}
