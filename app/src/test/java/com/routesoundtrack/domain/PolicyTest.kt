package com.routesoundtrack.domain

import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.DriveContext
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.domain.model.Road
import com.routesoundtrack.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyTest {
    private val cue = Cue(1, 20.0, CueKind.MID)
    private fun mood(preset: Preset, road: Road?, longDrive: Boolean, mode: MoodMode = MoodMode.AUTO) =
        Policy(Settings(preset = preset, moodMode = mode)).moodFor(cue, DriveContext(road, night = false, longDrive = longDrive))

    @Test
    fun `장거리 고속도로에서 90분 넘게 달리면 파워 단계`() {
        assertEquals(Mood.UPBEAT, mood(Preset.LONG_HAUL, Road.HIGHWAY, longDrive = false))
        assertEquals(Mood.HYPE, mood(Preset.LONG_HAUL, Road.HIGHWAY, longDrive = true))
    }

    @Test
    fun `파워는 시내·정체에서 자동으로 고르지 않는다`() {
        assertEquals(Mood.CHILL, mood(Preset.LONG_HAUL, Road.CITY, longDrive = true))
        assertEquals(Mood.CHILL, mood(Preset.LONG_HAUL, Road.JAM, longDrive = true))
    }

    @Test
    fun `사용자가 고른 곡 성격이 우선한다`() {
        assertEquals(Mood.HYPE, mood(Preset.TRIP, Road.CITY, longDrive = false, mode = MoodMode.HYPE))
        assertEquals(Mood.CHILL, mood(Preset.LONG_HAUL, Road.HIGHWAY, longDrive = true, mode = MoodMode.CHILL))
        assertEquals(Mood.GROOVE, mood(Preset.FAMILY, Road.HIGHWAY, longDrive = false, mode = MoodMode.GROOVE))
    }
}
