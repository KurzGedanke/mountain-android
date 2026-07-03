package de.thorejahn.mountain.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.thorejahn.mountain.R
import de.thorejahn.mountain.ui.Destination
import de.thorejahn.mountain.ui.LocalNav
import de.thorejahn.mountain.ui.LocalOpenUrl
import de.thorejahn.mountain.ui.common.LinkRow
import de.thorejahn.mountain.ui.common.NavRow
import de.thorejahn.mountain.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val openUrl = LocalOpenUrl.current

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.info_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Official festival links
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.official_links))
                LinkRow(Icons.Filled.Newspaper, Color(0xFFD32F2F), stringResource(R.string.link_news), "dongopenair.de/news") {
                    openUrl("https://www.dongopenair.de/news/")
                }
                LinkRow(Icons.AutoMirrored.Filled.HelpOutline, Color(0xFF1976D2), stringResource(R.string.link_faq), "dongopenair.de/infos") {
                    openUrl("https://www.dongopenair.de/infos/")
                }
                LinkRow(Icons.Filled.Directions, Color(0xFF388E3C), stringResource(R.string.link_directions), "dongopenair.de/anfahrt") {
                    openUrl("https://www.dongopenair.de/anfahrt/")
                }
            }

            // Where to get the Android app
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.get_app))
                LinkRow(Icons.Filled.Download, Color(0xFF388E3C), stringResource(R.string.link_releases), "github.com/KurzGedanke/mountain-android/releases") {
                    openUrl("https://github.com/KurzGedanke/mountain-android/releases")
                }
                LinkRow(Icons.Filled.Code, Color(0xFF424242), stringResource(R.string.link_source), "github.com/KurzGedanke/mountain-android") {
                    openUrl("https://github.com/KurzGedanke/mountain-android")
                }
            }

            // App submenu
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.app_name))
                NavRow(Icons.Filled.Settings, stringResource(R.string.settings)) {
                    nav.push(Destination.Settings)
                }
                NavRow(Icons.Filled.AccountCircle, stringResource(R.string.about_title)) {
                    nav.push(Destination.About)
                }
            }

            // Legal disclaimer
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.disclaimer_title))
                Text(
                    text = stringResource(R.string.disclaimer_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
