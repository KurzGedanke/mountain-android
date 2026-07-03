package de.thorejahn.mountain.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import de.thorejahn.mountain.MountainApp
import de.thorejahn.mountain.data.prefs.AppearanceSetting
import de.thorejahn.mountain.ui.theme.MountainTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MountainApp).container
        val startTab = when (intent?.getStringExtra("startTab")) {
            "lineup" -> AppTab.LINEUP
            "info", "settings", "about" -> AppTab.INFO
            else -> AppTab.NOW
        }

        setContent {
            val nav = remember { NavController() }
            LaunchedEffect(Unit) { nav.selectTab(startTab) }

            val openUrl: (String) -> Unit = { url ->
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.onFailure {
                    Toast.makeText(this, url, Toast.LENGTH_SHORT).show()
                }
            }

            val darkTheme = when (container.settings.appearance) {
                AppearanceSetting.SYSTEM -> isSystemInDarkTheme()
                AppearanceSetting.LIGHT -> false
                AppearanceSetting.DARK -> true
            }

            MountainTheme(darkTheme = darkTheme, dynamicColor = false) {
                CompositionLocalProvider(
                    LocalLineupStore provides container.lineup,
                    LocalFavoritesStore provides container.favorites,
                    LocalAutographFavoritesStore provides container.autographFavorites,
                    LocalSettingsStore provides container.settings,
                    LocalReminderManager provides container.reminders,
                    LocalNav provides nav,
                    LocalOpenUrl provides openUrl,
                ) {
                    AppScaffold()
                }
            }
        }
    }
}
