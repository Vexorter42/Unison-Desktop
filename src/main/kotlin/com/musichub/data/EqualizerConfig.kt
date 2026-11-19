package com.musichub.data

import kotlinx.serialization.Serializable

/** Десять полос libVLC. Значения в дБ; CUSTOM сохраняет ручную настройку. */
@Serializable
enum class EqualizerPreset(val label: String, val gains: List<Float>) {
    FLAT("Ровно", List(10) { 0f }),
    BASS("Басы", listOf(6f, 5f, 3f, 1f, 0f, -1f, -1f, 0f, 0f, 0f)),
    VOCALS("Вокал", listOf(-2f, -1f, 0f, 2f, 3f, 3f, 2f, 1f, 0f, -1f)),
    ACOUSTIC("Акустика", listOf(2f, 2f, 1f, 0f, -1f, 1f, 2f, 2f, 1f, 0f)),
    ELECTRONIC("Электроника", listOf(5f, 4f, 1f, 0f, -1f, 0f, 1f, 3f, 4f, 4f)),
    ROCK("Рок", listOf(4f, 3f, 1f, -1f, -2f, 1f, 3f, 4f, 3f, 2f)),
    CLASSICAL("Классика", listOf(2f, 2f, 1f, 0f, 0f, 0f, 1f, 2f, 2f, 2f)),
    NIGHT("Тихий вечер", listOf(-3f, -2f, -1f, 1f, 2f, 2f, 1f, -1f, -2f, -3f)),
    CUSTOM("Своя", emptyList()),
}

@Serializable
data class EqualizerConfig(
    val enabled: Boolean = false,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val bands: List<Float> = List(10) { 0f },
    val preamp: Float = 0f,
    val safeHeadroom: Boolean = true,
) {
    fun withPreset(next: EqualizerPreset): EqualizerConfig =
        if (next == EqualizerPreset.CUSTOM) copy(preset = next)
        else copy(preset = next, bands = next.gains, preamp = 0f)

    /** Запас по уровню снижает риск цифрового клиппинга при усилении частот. */
    fun appliedPreamp(): Float = if (safeHeadroom)
        minOf(preamp, -(bands.maxOrNull() ?: 0f).coerceAtLeast(0f)) else preamp
}
