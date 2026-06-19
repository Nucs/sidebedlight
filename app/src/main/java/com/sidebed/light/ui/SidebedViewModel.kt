package com.sidebed.light.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sidebed.light.data.SidebedSettings
import com.sidebed.light.data.settingsRepository
import com.sidebed.light.schedule.ScheduleManager
import com.sidebed.light.service.ServiceController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bridges the settings repository + service control to the Compose UI. */
class SidebedViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repo = app.settingsRepository

    val settings: StateFlow<SidebedSettings> = repo.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SidebedSettings(),
    )

    fun arm() = ServiceController.arm(app)

    fun disarm() = ServiceController.disarm(app)

    /** Persist a settings change and keep the alarm schedule in sync. */
    fun update(transform: (SidebedSettings) -> SidebedSettings) {
        viewModelScope.launch {
            repo.update(transform)
            ScheduleManager.apply(app, repo.current())
        }
    }
}
