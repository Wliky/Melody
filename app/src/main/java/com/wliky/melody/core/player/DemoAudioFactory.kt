package com.wliky.melody.core.player

import android.content.Context
import com.wliky.melody.core.common.DispatchersProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.withContext

/**
 * 演示模式用的音频。运行时合成一段 24 秒的琶音写入缓存目录，
 * 这样「演示模式」下播放器、进度条、后台播放、通知控制都能真实跑起来，
 * 且仓库里不必带任何音频素材（不涉及任何版权内容）。
 */
@Singleton
class DemoAudioFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatchersProvider,
) {

    @Volatile
    private var cached: File? = null

    suspend fun ensureToneFile(): File = withContext(dispatchers.io) {
        cached?.takeIf { it.exists() && it.length() > 1024 }?.let { return@withContext it }
        val file = File(context.cacheDir, FILE_NAME)
        if (!file.exists() || file.length() <= 1024) {
            runCatching { writeTone(file) }
        }
        if (file.exists()) cached = file
        file
    }

    suspend fun toneUri(): String? = ensureToneFile().takeIf { it.exists() }?.let { "file://${it.absolutePath}" }

    private fun writeTone(file: File) {
        val sampleRate = 16_000
        val noteLength = 0.75
        val notes = doubleArrayOf(261.63, 329.63, 392.00, 523.25, 392.00, 329.63)
        val totalSeconds = 24.0
        val totalSamples = (sampleRate * totalSeconds).toInt()

        BufferedOutputStream(FileOutputStream(file)).use { out ->
            val dataSize = totalSamples * 2
            out.write("RIFF".toByteArray())
            out.writeLittleEndianInt(36 + dataSize)
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.writeLittleEndianInt(16)
            out.writeLittleEndianShort(1) // PCM
            out.writeLittleEndianShort(1) // 单声道
            out.writeLittleEndianInt(sampleRate)
            out.writeLittleEndianInt(sampleRate * 2)
            out.writeLittleEndianShort(2)
            out.writeLittleEndianShort(16)
            out.write("data".toByteArray())
            out.writeLittleEndianInt(dataSize)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val noteIndex = ((t / noteLength).toInt()) % notes.size
                val notePos = (t % noteLength) / noteLength
                // 每个音：快速起音 + 指数衰减
                val envelope = min(1.0, notePos / 0.04) * kotlin.math.exp(-2.4 * notePos)
                // 整体首尾淡入淡出，避免爆音
                val global = min(1.0, min(t / 0.6, (totalSeconds - t) / 1.2)).coerceAtLeast(0.0)
                val fundamental = sin(2 * PI * notes[noteIndex] * t)
                val harmonic = 0.25 * sin(2 * PI * notes[noteIndex] * 2 * t)
                val sample = ((fundamental + harmonic) / 1.25) * envelope * global * 0.42
                val value = (sample * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
                out.writeLittleEndianShort(value)
            }
        }
    }

    private fun BufferedOutputStream.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun BufferedOutputStream.writeLittleEndianShort(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private companion object {
        const val FILE_NAME = "melody_demo_tone.wav"
    }
}
