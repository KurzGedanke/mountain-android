package de.thorejahn.mountain.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import de.thorejahn.mountain.R
import de.thorejahn.mountain.data.model.Band
import de.thorejahn.mountain.data.model.TimeSlot
import de.thorejahn.mountain.data.model.imageUrl
import de.thorejahn.mountain.domain.LineupStore
import de.thorejahn.mountain.ui.Destination
import de.thorejahn.mountain.ui.LocalFavoritesStore
import de.thorejahn.mountain.ui.LocalLineupStore
import de.thorejahn.mountain.ui.LocalNav
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** Self-updating "now" clock — recomposes every [periodMs] (§7.1 live tick). */
@Composable
fun rememberNow(periodMs: Long = 30_000L): State<Instant> =
    produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(periodMs)
        }
    }

/** A music-mic glyph on a low-emphasis fill — the artwork/thumbnail placeholder (§8.2). */
@Composable
fun MicPlaceholder(iconSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Square band thumbnail (§8.2). */
@Composable
fun BandThumbnail(band: Band?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val shape = RoundedCornerShape(size * 0.18f)
    val url = band?.imageUrl
    Box(modifier = modifier.size(size).clip(shape)) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = band.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { MicPlaceholder(size * 0.4f, Modifier.fillMaxSize()) },
                error = { MicPlaceholder(size * 0.4f, Modifier.fillMaxSize()) },
                success = { SubcomposeAsyncImageContent() },
            )
        } else {
            MicPlaceholder(size * 0.4f, Modifier.fillMaxSize())
        }
    }
}

/** Star toggle (§8.3). */
@Composable
fun FavoriteButton(bandId: Int, modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    val favorites = LocalFavoritesStore.current
    val isFav = favorites.isFavorite(bandId)
    IconButton(onClick = { favorites.toggle(bandId) }, modifier = modifier) {
        Icon(
            imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(
                if (isFav) R.string.remove_favorite else R.string.add_favorite
            ),
            tint = if (isFav) Color(0xFFFFC400) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Shared toolbar refresh action (§8.4): spinner while loading, else a refresh icon. */
@Composable
fun RefreshButton() {
    val lineup = LocalLineupStore.current
    val scope = rememberCoroutineScope()
    val loading = lineup.status == LineupStore.Status.Loading
    IconButton(
        onClick = { if (!loading) scope.launch { lineup.refresh() } },
        enabled = !loading,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.lineup_title))
        }
    }
}

/** Full-screen content-unavailable view (§7). */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Shared list row used by the Now board (§7.1 SlotRow). */
@Composable
fun SlotRow(
    slot: TimeSlot,
    emphasized: Boolean,
    showDay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val lineup = LocalLineupStore.current
    val nav = LocalNav.current
    val band = lineup.band(slot.bandId)
    val subtitle = if (showDay) Fmt.dayTime(slot.start) else Fmt.range(slot.start, slot.end)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { nav.push(Destination.BandDetail(slot.bandId)) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BandThumbnail(band)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slot.band,
                style = if (emphasized) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = stringResource(R.string.dot_separator, subtitle, slot.stage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FavoriteButton(slot.bandId)
    }
}

/** Schedule list row (§7.2 ScheduleRow). */
@Composable
fun ScheduleRow(slot: TimeSlot, modifier: Modifier = Modifier) {
    val lineup = LocalLineupStore.current
    val nav = LocalNav.current
    val band = lineup.band(slot.bandId)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { nav.push(Destination.BandDetail(slot.bandId)) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = Fmt.time(slot.start),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        BandThumbnail(band, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = slot.band, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = slot.stage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FavoriteButton(slot.bandId)
    }
}
