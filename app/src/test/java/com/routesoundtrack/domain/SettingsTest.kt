package com.routesoundtrack.domain

import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.domain.model.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    private val base = Settings(preset = Preset.TRIP, density = Density.FREQUENT, moodMode = MoodMode.AUTO, autoStart = false)

    @Test
    fun `주행 목적이나 음악 밀도가 바뀌면 재생 지점이 바뀐다`() {
        assertTrue(base.copy(preset = Preset.LONG_HAUL).movesCuesFrom(base))
        assertTrue(base.copy(density = Density.OCCASIONAL).movesCuesFrom(base))
    }

    @Test
    fun `곡 성격이나 자동 시작만 바뀌면 재생 지점은 그대로다`() {
        assertFalse(base.copy(moodMode = MoodMode.HYPE, autoStart = true).movesCuesFrom(base))
        assertFalse(base.movesCuesFrom(base))
    }
}
