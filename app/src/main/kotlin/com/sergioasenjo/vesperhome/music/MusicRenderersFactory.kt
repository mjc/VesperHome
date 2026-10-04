package com.sergioasenjo.vesperhome.music

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class MusicRenderersFactory(context: Context, private val gainProcessor: MusicGainAudioProcessor) :
    DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf<AudioProcessor>(gainProcessor))
        .setEnableFloatOutput(false)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .build()
}
