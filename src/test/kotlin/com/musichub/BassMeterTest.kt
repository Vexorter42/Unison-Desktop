package com.musichub

import com.musichub.playback.BassMeter
import com.sun.jna.Memory
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.lang.reflect.Proxy
import javax.sound.sampled.SourceDataLine

class BassMeterTest {
    @Test
    fun vlcVolumeResetCannotMakeNextTrackLoud() {
        val meter = newMeter(initialVolume = 0)

        meter.setVolume(1f, false)
        playSample(meter)

        assertEquals(0, meter.outputPeak)
    }

    @Test
    fun appVolumeStillControlsOutput() {
        val meter = newMeter(initialVolume = 0)

        meter.setVolume(100)
        playSample(meter)

        assertEquals(12_000, meter.outputPeak)
    }

    private fun newMeter(initialVolume: Int): BassMeter {
        val line = Proxy.newProxyInstance(
            SourceDataLine::class.java.classLoader,
            arrayOf(SourceDataLine::class.java),
        ) { _, method, args ->
            when (method.name) {
                "write" -> args[2]
                "isOpen" -> true
                else -> null
            }
        } as SourceDataLine
        val constructor = BassMeter::class.java.getDeclaredConstructor(SourceDataLine::class.java, Int::class.javaPrimitiveType)
        return constructor.apply { isAccessible = true }.newInstance(line, initialVolume)
    }

    private fun playSample(meter: BassMeter) {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
            .apply { isAccessible = true }.get(null)
        val player = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, MediaPlayer::class.java) as MediaPlayer
        val samples = Memory(4).apply {
            setShort(0, 12_000)
            setShort(2, 12_000)
        }
        meter.play(player, samples, 1, 0)
    }
}
