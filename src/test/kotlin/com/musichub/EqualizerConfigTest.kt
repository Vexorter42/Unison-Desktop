package com.musichub

import com.musichub.data.EqualizerConfig
import com.musichub.data.EqualizerPreset
import com.musichub.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EqualizerConfigTest {
    @Test fun presetsHaveTenBandsAndSafeLevels() {
        EqualizerPreset.entries.filterNot { it == EqualizerPreset.CUSTOM }.forEach { preset ->
            assertEquals(10, preset.gains.size)
            assertTrue(preset.gains.all { it in -12f..12f })
            val config = EqualizerConfig().withPreset(preset)
            assertEquals(preset, config.preset)
            assertTrue(config.appliedPreamp() + config.bands.max() <= 0f)
        }
    }

    @Test fun headroomCanBeDisabledAndSettingsSurviveRestart() = runBlocking {
        val file = Files.createTempFile("unison-equalizer", ".json").toFile()
        try {
            val repo = SettingsRepository(file)
            val custom = EqualizerConfig(enabled = true, preset = EqualizerPreset.CUSTOM,
                bands = listOf(6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), preamp = 2f)
            assertEquals(-6f, custom.appliedPreamp())
            assertEquals(2f, custom.copy(safeHeadroom = false).appliedPreamp())
            repo.update { it.copy(equalizer = custom) }
            val restored = SettingsRepository(file).current.equalizer
            assertEquals(custom, restored)
            assertTrue(restored.enabled)
            assertFalse(restored.bands.all { it == 0f })
        } finally {
            file.delete()
        }
    }
}
