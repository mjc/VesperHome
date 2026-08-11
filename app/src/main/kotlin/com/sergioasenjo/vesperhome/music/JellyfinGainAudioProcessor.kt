package com.sergioasenjo.vesperhome.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class JellyfinGainAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var gainFactor = UNITY_GAIN
    private var limiterGain = UNITY_GAIN
    private var limiterReleaseStep = 0f
    private var frameSamples = FloatArray(0)

    fun setGainDb(gainDb: Double?) {
        val safeGainDb = gainDb
            ?.takeIf(Double::isFinite)
            ?.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            ?: 0.0
        gainFactor = 10.0.pow(safeGainDb / 20.0).toFloat()
        limiterGain = UNITY_GAIN
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        frameSamples = FloatArray(inputAudioFormat.channelCount)
        limiterReleaseStep = 1f - exp(-1f / (inputAudioFormat.sampleRate * LIMITER_RELEASE_SECONDS))
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val gain = gainFactor
        if (gain == UNITY_GAIN && limiterGain == UNITY_GAIN) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        while (inputBuffer.hasRemaining()) {
            var peak = 0f
            for (channel in frameSamples.indices) {
                val sample = when (inputAudioFormat.encoding) {
                    C.ENCODING_PCM_16BIT -> inputBuffer.short / PCM_16_SCALE
                    C.ENCODING_PCM_FLOAT -> inputBuffer.float.takeIf(Float::isFinite) ?: 0f
                    else -> error("Unsupported PCM encoding")
                }
                frameSamples[channel] = sample * gain
                peak = maxOf(peak, abs(frameSamples[channel]))
            }

            val requiredLimiterGain = if (peak > LIMITER_THRESHOLD) LIMITER_THRESHOLD / peak else UNITY_GAIN
            limiterGain = if (requiredLimiterGain < limiterGain) {
                requiredLimiterGain
            } else {
                limiterGain + (UNITY_GAIN - limiterGain) * limiterReleaseStep
            }

            frameSamples.forEach { amplifiedSample ->
                val limitedSample = (amplifiedSample * limiterGain).coerceIn(-UNITY_GAIN, UNITY_GAIN)
                when (inputAudioFormat.encoding) {
                    C.ENCODING_PCM_16BIT -> output.putShort((limitedSample * PCM_16_MAX).roundToInt().toShort())
                    C.ENCODING_PCM_FLOAT -> output.putFloat(limitedSample)
                    else -> error("Unsupported PCM encoding")
                }
            }
        }
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        limiterGain = UNITY_GAIN
    }

    private companion object {
        const val MIN_GAIN_DB = -20.0
        const val MAX_GAIN_DB = 6.0
        const val UNITY_GAIN = 1f
        const val LIMITER_THRESHOLD = 0.98f
        const val LIMITER_RELEASE_SECONDS = 0.1f
        const val PCM_16_SCALE = 32768f
        const val PCM_16_MAX = 32767f
    }
}
