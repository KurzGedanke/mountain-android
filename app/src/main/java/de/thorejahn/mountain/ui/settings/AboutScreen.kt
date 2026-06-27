package de.thorejahn.mountain.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.thorejahn.mountain.R
import de.thorejahn.mountain.ui.LocalNav
import de.thorejahn.mountain.ui.LocalOpenUrl
import de.thorejahn.mountain.ui.common.LinkRow
import de.thorejahn.mountain.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val openUrl = LocalOpenUrl.current
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.thore),
                    contentDescription = "Thore",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(128.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Text("Thore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.about_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Bio
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.bio_1), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.bio_2), style = MaterialTheme.typography.bodyLarge)
            }

            // Open Source
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.open_source))
                Text(
                    text = stringResource(R.string.open_source_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinkRow(Icons.Filled.Code, Color(0xFF8E24AA), "Mountain", "github.com/KurzGedanke/mountain") {
                    openUrl("https://github.com/KurzGedanke/mountain")
                }
                LinkRow(Icons.Filled.Dns, Color(0xFF00897B), "Band API", "github.com/KurzGedanke/band-api") {
                    openUrl("https://github.com/KurzGedanke/band-api")
                }
            }

            // Find me online
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.find_me_online))
                LinkRow(Icons.AutoMirrored.Filled.Chat, Color(0xFF6163FF), "Mastodon", "@kurzgedanke@chaos.social") {
                    openUrl("https://chaos.social/@kurzgedanke")
                }
                LinkRow(Icons.Filled.Cloud, Color(0xFF0087FF), "Bluesky", "@kurzgedanke.de") {
                    openUrl("https://bsky.app/profile/kurzgedanke.de")
                }
            }

            // Contact
            LinkRow(
                Icons.Filled.Email,
                Color(0xFFFB8C00),
                stringResource(R.string.questions_or_bugs),
                "app@kurzgedanke.me",
                modifier = Modifier.fillMaxWidth(),
            ) {
                openUrl("mailto:app@kurzgedanke.me")
            }

            // Footer
            Text(
                text = "Mountain v$version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
