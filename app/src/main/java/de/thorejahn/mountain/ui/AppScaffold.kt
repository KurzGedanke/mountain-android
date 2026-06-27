package de.thorejahn.mountain.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import de.thorejahn.mountain.R
import de.thorejahn.mountain.ui.band.BandDetailScreen
import de.thorejahn.mountain.ui.home.HomeScreen
import de.thorejahn.mountain.ui.lineup.RunningOrderScreen
import de.thorejahn.mountain.ui.info.InfoScreen
import de.thorejahn.mountain.ui.settings.AboutScreen
import de.thorejahn.mountain.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun AppScaffold() {
    val nav = LocalNav.current
    val lineup = LocalLineupStore.current
    val favorites = LocalFavoritesStore.current
    val settings = LocalSettingsStore.current
    val reminders = LocalReminderManager.current
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        reminders.refreshAuthorization()
        scope.launch { reminders.sync(settings.remindersEnabled, favorites.ids, lineup.slots) }
    }

    // Startup: refresh (falls back to cache) then arm reminders (§5.1).
    LaunchedEffect(Unit) {
        lineup.refresh()
        reminders.sync(settings.remindersEnabled, favorites.ids, lineup.slots)
    }

    // Reactive re-sync whenever reminders-enabled or favorites change (§5.2).
    LaunchedEffect(settings.remindersEnabled, favorites.ids) {
        val enabled = settings.remindersEnabled
        val favs = favorites.ids
        val needsPermission = enabled && favs.isNotEmpty() && !reminders.authorized &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            reminders.sync(enabled, favs, lineup.slots)
        }
    }

    BackHandler(enabled = nav.current != null) { nav.pop() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TabSpec.entries.forEach { spec ->
                    NavigationBarItem(
                        selected = nav.tab == spec.tab && nav.current == null,
                        onClick = { nav.selectTab(spec.tab) },
                        icon = { Icon(spec.icon, contentDescription = null) },
                        label = { Text(stringResource(spec.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (val dest = nav.current) {
                is Destination.BandDetail -> BandDetailScreen(dest.bandId)
                Destination.Settings -> SettingsScreen()
                Destination.About -> AboutScreen()
                null -> when (nav.tab) {
                    AppTab.NOW -> HomeScreen()
                    AppTab.LINEUP -> RunningOrderScreen()
                    AppTab.INFO -> InfoScreen()
                }
            }
        }
    }
}

private enum class TabSpec(
    val tab: AppTab,
    val labelRes: Int,
    val icon: ImageVector,
) {
    NOW(AppTab.NOW, R.string.now, Icons.Filled.PlayCircle),
    LINEUP(AppTab.LINEUP, R.string.lineup_title, Icons.AutoMirrored.Filled.List),
    INFO(AppTab.INFO, R.string.info_title, Icons.Filled.Info),
}
