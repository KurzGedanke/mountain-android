package de.thorejahn.mountain.ui.band

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import de.thorejahn.mountain.R
import de.thorejahn.mountain.analytics.Telemetry
import de.thorejahn.mountain.data.model.appleMusicUrl
import de.thorejahn.mountain.data.model.bandcampUrl
import de.thorejahn.mountain.data.model.descriptionText
import de.thorejahn.mountain.data.model.hasLinks
import de.thorejahn.mountain.data.model.imageUrl
import de.thorejahn.mountain.data.model.instagramUrl
import de.thorejahn.mountain.data.model.spotifyUrl
import de.thorejahn.mountain.ui.LocalLineupStore
import de.thorejahn.mountain.ui.LocalNav
import de.thorejahn.mountain.ui.LocalOpenUrl
import de.thorejahn.mountain.ui.common.AutographReminderButton
import de.thorejahn.mountain.ui.common.FavoriteButton
import de.thorejahn.mountain.ui.common.Fmt
import de.thorejahn.mountain.ui.common.MicPlaceholder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandDetailScreen(bandId: Int, modifier: Modifier = Modifier) {
    val lineup = LocalLineupStore.current
    val nav = LocalNav.current
    val openUrl = LocalOpenUrl.current
    val band = lineup.band(bandId)

    LaunchedEffect(bandId) { Telemetry.bandViewed(bandId) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(band?.name ?: stringResource(R.string.band)) },
                navigationIcon = {
                    IconButton(onClick = { nav.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = { if (band != null) FavoriteButton(bandId, iconSize = 28.dp) },
            )
        },
    ) { innerPadding ->
        if (band == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                de.thorejahn.mountain.ui.common.EmptyState(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = stringResource(R.string.band_not_found),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1. Header artwork
            band.imageUrl?.let { url ->
                SubcomposeAsyncImage(
                    model = url,
                    contentDescription = band.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    loading = {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            MicPlaceholder(64.dp, Modifier.fillMaxSize())
                            CircularProgressIndicator()
                        }
                    },
                    error = { MicPlaceholder(64.dp, Modifier.fillMaxSize()) },
                    success = { SubcomposeAsyncImageContent() },
                )
            }

            // 2. Genre
            band.genre?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 3. Set times
            val sets = lineup.slotsForBand(bandId).sortedBy { it.start }
            if (sets.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sets.forEach { slot ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.dot_separator,
                                    Fmt.dayTime(slot.start),
                                    slot.stage,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }

            // 3b. Autographs (§6) — only when the band has sessions; styled like the set-times card.
            val autographs = lineup.autographsForBand(bandId)
            if (autographs.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.autographs_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    autographs.forEach { session ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Draw,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        R.string.dot_separator,
                                        Fmt.dayTime(session.start),
                                        session.signingPoint,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                session.location?.takeIf { it.isNotEmpty() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            AutographReminderButton(session.id)
                        }
                    }
                }
            }

            // 4. Description
            band.descriptionText?.takeIf { it.isNotEmpty() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge)
            }

            // 5. Links
            if (band.hasLinks) {
                Text(
                    text = stringResource(R.string.listen_and_follow),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    band.spotifyUrl?.let {
                        LinkButton(Icons.Filled.MusicNote, "Spotify", Modifier.weight(1f)) { openUrl(it) }
                    }
                    band.appleMusicUrl?.let {
                        LinkButton(Icons.Filled.Album, "Apple Music", Modifier.weight(1f)) { openUrl(it) }
                    }
                    band.bandcampUrl?.let {
                        LinkButton(Icons.Filled.GraphicEq, "Bandcamp", Modifier.weight(1f)) { openUrl(it) }
                    }
                    band.instagramUrl?.let {
                        LinkButton(Icons.Filled.PhotoCamera, "Instagram", Modifier.weight(1f)) { openUrl(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}
