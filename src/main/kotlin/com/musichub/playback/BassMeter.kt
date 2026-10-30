package com.musichub.playback

import com.sun.jna.Pointer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Вывод звука через приложение вместо собственного вывода VLC: VLC отдаёт сэмплы, мы меряем по ним силу басов
 * (от неё пульсирует сфера волны), применяем громкость и пишем в звуковую карту.
 *
 * Громкость применяем сами (VLC вызывает [setVolume]), а не VLC: его программная громкость в 16 битах
 * на малых значениях обнуляет звук. Формат — только 16 бит: другие VLC 3 в этом режиме не отдаёт.
 */
class BassMeter private constructor(private val line: SourceDataLine, initialVolume: Int) : AudioCallbackAdapter() {

    /** Сила басов 0..1, сглаженная. Читается интерфейсом каждый кадр. */
    @Volatile
    var level: Float = 0f
        private set

    /** Сколько блоков звука прошло — для проверки. */
    @Volatile
    var blocks: Long = 0
        private set

    /** Пик последнего отправленного в звуковую карту блока (0..32767) — для проверки. */
    @Volatile
    var outputPeak: Int = 0
        private set

    @Volatile
    private var gain = curve(initialVolume / 100f)

    private var input = ByteArray(0)
    private var output = ByteArray(0)
    private var lowPass = 0.0
    private var peak = 0.02

    override fun play(mediaPlayer: MediaPlayer, samples: Pointer, sampleCount: Int, pts: Long) {
        val bytes = sampleCount * FRAME_BYTES
        if (input.size < bytes) {
            input = ByteArray(bytes)
            output = ByteArray(bytes)
        }
        samples.read(0, input, 0, bytes)
        blocks++
        measure(sampleCount)
        val g = gain
        var outPeak = 0
        for (i in 0 until sampleCount * CHANNELS) {
            val s = ((input[2 * i].toInt() and 0xFF) or (input[2 * i + 1].toInt() shl 8)).toShort()
            val v = (s * g).toInt().coerceIn(-32768, 32767)
            outPeak = max(outPeak, kotlin.math.abs(v))
            output[2 * i] = v.toByte()
            output[2 * i + 1] = (v shr 8).toByte()
        }
        outputPeak = outPeak
        line.write(output, 0, bytes)
    }

    /** Басы — то, что проходит фильтр нижних частот (~120 Гц); уровень — относительно недавнего пика (до громкости). */
    private fun measure(frames: Int) {
        if (frames == 0) return
        var energy = 0.0
        for (i in 0 until frames) {
            val o = i * FRAME_BYTES
            val l = ((input[o].toInt() and 0xFF) or (input[o + 1].toInt() shl 8)).toShort()
            val r = ((input[o + 2].toInt() and 0xFF) or (input[o + 3].toInt() shl 8)).toShort()
            val x = (l + r) / 65536.0
            lowPass += LOW_PASS * (x - lowPass)
            energy += lowPass * lowPass
        }
        val rms = sqrt(energy / frames)
        peak = max(rms, peak * 0.998).coerceAtLeast(0.003)
        val target = (rms / peak).coerceIn(0.0, 1.0).let { it * it }.toFloat()
        // Быстрая атака, медленный спад — удар баса виден, но картинка не дёргается
        level += (target - level) * if (target > level) 0.55f else 0.12f
    }

    /**
     * VLC сообщает громкость 0..1. Флаг mute не используем: vlcj читает сишный bool неверно и он всегда true,
     * а своей кнопки «без звука» у Unison нет — тишина это громкость 0.
     */
    override fun setVolume(volume: Float, mute: Boolean) {
        gain = curve(volume)
    }

    override fun pause(mediaPlayer: MediaPlayer, pts: Long) {
        line.stop()
        level = 0f
    }

    override fun resume(mediaPlayer: MediaPlayer, pts: Long) = line.start()

    override fun flush(mediaPlayer: MediaPlayer, pts: Long) {
        line.flush()
        level = 0f
    }

    override fun drain(mediaPlayer: MediaPlayer) = line.drain()

    fun close() = runCatching { line.close() }

    companion object {
        const val RATE = 44100
        const val CHANNELS = 2
        const val VLC_FORMAT = "S16N"
        private const val FRAME_BYTES = 2 * CHANNELS
        private val LOW_PASS = 1.0 - exp(-2.0 * Math.PI * 120.0 / RATE)

        /** Как у вывода VLC в Windows: ползунок громкости — кубическая кривая, чтобы на слух было равномерно. */
        private fun curve(v: Float): Float = v.coerceIn(0f, 1.25f).let { it * it * it }

        /** null — звуковое устройство не открылось: тогда остаётся обычный вывод VLC. */
        fun open(initialVolume: Int): BassMeter? = runCatching {
            val format = AudioFormat(RATE.toFloat(), 16, CHANNELS, true, false)
            val line = AudioSystem.getSourceDataLine(format)
            // ~150 мс буфера: меньше — риск щелчков, больше — сфера заметно опережает звук
            line.open(format, RATE * FRAME_BYTES * 15 / 100)
            line.start()
            BassMeter(line, initialVolume)
        }.getOrNull()
    }
}
