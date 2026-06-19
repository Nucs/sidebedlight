package com.sidebed.light.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidebed.light.Permissions
import com.sidebed.light.data.LightMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SidebedViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SectionCard("Brightness") {
                LabeledSlider("Minimum (gentle move)", s.minBrightnessPct, 1..100, "${s.minBrightnessPct}%") { v ->
                    vm.update { it.copy(minBrightnessPct = v) }
                }
                LabeledSlider("Maximum (full shake)", s.maxBrightnessPct, 1..100, "${s.maxBrightnessPct}%") { v ->
                    vm.update { it.copy(maxBrightnessPct = v) }
                }
            }

            SectionCard("Motion") {
                LabeledSlider("Sensitivity", s.sensitivityPct, 0..100, "${s.sensitivityPct}%") { v ->
                    vm.update { it.copy(sensitivityPct = v) }
                }
                LabeledSlider("Shake strength for max", s.shakeStrengthPct, 0..100, "${s.shakeStrengthPct}%") { v ->
                    vm.update { it.copy(shakeStrengthPct = v) }
                }
                LabeledSlider("Turn off after", s.offDelaySeconds, 2..30, "${s.offDelaySeconds}s") { v ->
                    vm.update { it.copy(offDelaySeconds = v) }
                }
            }

            SectionCard("Light mode") {
                ModeSelector(s.lightMode) { mode -> vm.update { it.copy(lightMode = mode) } }
                if (s.lightMode == LightMode.RED_SCREEN) {
                    LabeledSlider("Red brightness", s.redBrightnessPct, 5..100, "${s.redBrightnessPct}%") { v ->
                        vm.update { it.copy(redBrightnessPct = v) }
                    }
                    Text(
                        "Red mode draws on the screen (LEDs are white only) and needs the " +
                            "screen on. Grant “Display over other apps” below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("Auto schedule") {
                ToggleRow("Enable nightly schedule", checked = s.scheduleEnabled) { c ->
                    vm.update { it.copy(scheduleEnabled = c) }
                }
                if (s.scheduleEnabled) {
                    TimeRow("Turn on at", s.scheduleStartMinutes) { showStartPicker = true }
                    TimeRow("Turn off at", s.scheduleEndMinutes) { showEndPicker = true }
                }
            }

            SectionCard("Behaviour") {
                ToggleRow(
                    label = "Volume to zero turns off",
                    subtitle = "Take media or ring volume down to 0 to disarm",
                    checked = s.volumeOffGesture,
                ) { c -> vm.update { it.copy(volumeOffGesture = c) } }
                ToggleRow(
                    label = "Keep sensing with screen off",
                    subtitle = "Holds a wake lock — best while charging on the nightstand",
                    checked = s.wakeLockEnabled,
                ) { c -> vm.update { it.copy(wakeLockEnabled = c) } }
            }

            PermissionsSection()

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialMinutes = s.scheduleStartMinutes,
            onConfirm = { m -> vm.update { it.copy(scheduleStartMinutes = m) }; showStartPicker = false },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinutes = s.scheduleEndMinutes,
            onConfirm = { m -> vm.update { it.copy(scheduleEndMinutes = m) }; showEndPicker = false },
            onDismiss = { showEndPicker = false },
        )
    }
}

@Composable
private fun PermissionsSection() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    val hasNotif = remember(refresh) { Permissions.hasNotifications(context) }
    val hasOverlay = remember(refresh) { Permissions.hasOverlay(context) }
    val hasExact = remember(refresh) { Permissions.canScheduleExactAlarms(context) }

    SectionCard("Permissions") {
        PermissionRow("Notifications", hasNotif) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(Permissions.appDetailsIntent(context))
            }
        }
        PermissionRow("Display over other apps (red mode)", hasOverlay) {
            context.startActivity(Permissions.overlaySettingsIntent(context))
        }
        PermissionRow("Exact alarms (schedule)", hasExact) {
            context.startActivity(Permissions.exactAlarmSettingsIntent(context))
        }
    }
}
