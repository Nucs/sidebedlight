package com.sidebed.light

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidebed.light.ui.HomeScreen
import com.sidebed.light.ui.SettingsScreen
import com.sidebed.light.ui.SidebedViewModel
import com.sidebed.light.ui.theme.SidebedLightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SidebedLightTheme {
                SidebedRoot()
            }
        }
    }
}

@Composable
private fun SidebedRoot() {
    val vm: SidebedViewModel = viewModel()
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val armed by SidebedState.isArmed.collectAsStateWithLifecycle()

    // Arming needs the notification permission on Android 13+; request it once.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.arm() }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(vm = vm, onBack = { showSettings = false })
    } else {
        HomeScreen(
            vm = vm,
            onOpenSettings = { showSettings = true },
            onOpenRedLight = {
                context.startActivity(Intent(context, RedLightActivity::class.java))
            },
            onActivate = {
                when {
                    armed -> vm.disarm()
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !Permissions.hasNotifications(context) ->
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                    else -> vm.arm()
                }
            },
        )
    }
}
