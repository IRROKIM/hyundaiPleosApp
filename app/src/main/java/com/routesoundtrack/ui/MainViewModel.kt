package com.routesoundtrack.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.session.DriveSession
import com.routesoundtrack.session.UiState
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(private val session: DriveSession) : ViewModel() {
    val state: StateFlow<UiState> = session.ui

    fun toggle() = session.toggle()
    fun startWithMood(m: MoodMode) = session.startWithMood(m)
    fun refreshRoute() = session.refreshRoute()
    fun setPreset(p: Preset) = session.updateSettings { it.copy(preset = p) }
    fun setDensity(d: Density) = session.updateSettings { it.copy(density = d) }
    fun setMoodMode(m: MoodMode) = session.updateSettings { it.copy(moodMode = m) }
    fun setAutoStart(on: Boolean) = session.updateSettings { it.copy(autoStart = on) }

    companion object {
        fun factory(session: DriveSession) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(session) as T
        }
    }
}
