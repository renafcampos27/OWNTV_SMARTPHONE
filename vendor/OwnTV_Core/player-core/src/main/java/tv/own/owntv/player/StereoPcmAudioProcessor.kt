package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** Opt-in PCM16 stereo conversion for Android's standard 1..8 channel orders.
 * Front left/right stay separate, center/dialogue reaches both speakers, and surround/LFE
 * contributions are normalized to keep correlated full-scale input within full scale.
 * Unsupported channel counts fail explicitly instead of silently dropping dialogue.
 */
@UnstableApi
internal class StereoPcmAudioProcessor : BaseAudioProcessor() {
    private var coefficients = emptyArray<FloatArray>()
    private var pendingCoefficients = emptyArray<FloatArray>()
    private var channelPositions: IntArray? = null

    /** Positional masks use ascending bit order, matching Android interleaved PCM. */
    fun setChannelLayout(mask: Int?, mapping: IntArray?, channels: Int) {
        val positions = mask?.let { value -> (0..30).filter { value and (1 shl it) != 0 }.map { 1 shl it }.toIntArray() }
            ?: standardChannelPositions(channels)
        channelPositions = if (mapping != null) mapping.map { positions[it] }.toIntArray() else positions
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..8) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val positions = channelPositions
        if (positions != null && positions.size != inputAudioFormat.channelCount) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        pendingCoefficients = try {
            if (positions == null) stereoMixCoefficients(inputAudioFormat.channelCount)
            else stereoMixForPositions(positions)
        } catch (_: IllegalArgumentException) { throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat) }
        return if (inputAudioFormat.channelCount == 2 && (positions == null || positions.contentEquals(intArrayOf(4, 8)))) AudioProcessor.AudioFormat.NOT_SET
        else AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) { coefficients = pendingCoefficients }
    override fun onReset() { coefficients = emptyArray(); pendingCoefficients = emptyArray(); channelPositions = null }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        val output = replaceOutputBuffer(frames * 4)
        repeat(frames) {
            var left = 0f
            var right = 0f
            for (channel in coefficients.indices) {
                val sample = inputBuffer.short.toFloat()
                left += sample * coefficients[channel][0]
                right += sample * coefficients[channel][1]
            }
            output.putShort(left.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            output.putShort(right.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        output.flip()
    }
}

/** Interleaving: FL FR [FC] [LFE] [BL BR] [BC or SL SR], as Android's default masks specify. */
internal fun stereoMixCoefficients(channels: Int): Array<FloatArray> {
    require(channels in 1..8)
    return stereoMixForPositions(standardChannelPositions(channels))
}

private fun standardChannelPositions(channels: Int): IntArray {
    require(channels in 1..8)
    return when (channels) {
        1 -> intArrayOf(16)
        2 -> intArrayOf(4, 8)
        3 -> intArrayOf(4, 8, 16)
        4 -> intArrayOf(4, 8, 64, 128)
        5 -> intArrayOf(4, 8, 16, 64, 128)
        6 -> intArrayOf(4, 8, 16, 32, 64, 128)
        7 -> intArrayOf(4, 8, 16, 32, 64, 128, 1024)
        else -> intArrayOf(4, 8, 16, 32, 64, 128, 2048, 4096)
    }
}

/** Android positional channel bits. Unknown/height layouts require an explicit future policy. */
internal fun stereoMixForPositions(positions: IntArray): Array<FloatArray> {
    val matrix = positions.map { position ->
        when (position) {
            4 -> floatArrayOf(1f, 0f) // front left
            8 -> floatArrayOf(0f, 1f) // front right
            16 -> floatArrayOf(0.70710677f, 0.70710677f) // center
            32, 1024 -> floatArrayOf(0.5f, 0.5f) // LFE/back center
            64, 256, 2048 -> floatArrayOf(0.70710677f, 0f) // back/front-of-center/side left
            128, 512, 4096 -> floatArrayOf(0f, 0.70710677f) // corresponding right
            else -> throw IllegalArgumentException("Unsupported PCM channel position: $position")
        }
    }.toTypedArray()
    if (positions.size == 1) { matrix[0][0] = 1f; matrix[0][1] = 1f }
    val peak = maxOf(matrix.sumOf { it[0].toDouble() }, matrix.sumOf { it[1].toDouble() }, 1.0).toFloat()
    matrix.forEach { it[0] /= peak; it[1] /= peak }
    return matrix
}
