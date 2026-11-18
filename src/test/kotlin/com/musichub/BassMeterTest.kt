package com.musichub

import com.musichub.playback.BassMeter
import com.sun.jna.Memory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        playSample(meter, frames = 441)
        assertTrue("громкость не должна прыгать при клике", meter.outputPeak in 1..3000)
        playSample(meter, frames = 44_100)

        assertTrue("громкость должна достичь цели", meter.outputPeak > 11_800)
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

    private fun playSample(meter: BassMeter, frames: Int = 1) {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
            .apply { isAccessible = true }.get(null)
        val player = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, MediaPlayer::class.java) as MediaPlayer
        val samples = Memory(frames * 4L).apply {
            repeat(frames) { i ->
                setShort(i * 4L, 12_000)
                setShort(i * 4L + 2, 12_000)
            }
        }
        meter.play(player, samples, frames, 0)
    }
}
