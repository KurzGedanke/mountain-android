package de.thorejahn.mountain.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.thorejahn.mountain.R
import de.thorejahn.mountain.domain.LineupStore
import de.thorejahn.mountain.ui.LocalAutographFavoritesStore
import de.thorejahn.mountain.ui.LocalFavoritesStore
import de.thorejahn.mountain.ui.LocalLineupStore
import de.thorejahn.mountain.ui.common.AutographHomeRow
import de.thorejahn.mountain.ui.common.EmptyState
import de.thorejahn.mountain.ui.common.RefreshButton
import de.thorejahn.mountain.ui.common.SlotRow
import de.thorejahn.mountain.ui.common.rememberNow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val lineup = LocalLineupStore.current
    val favorites = LocalFavoritesStore.current
    val autographFavorites = LocalAutographFavoritesStore.current
    val scope = rememberCoroutineScope()
    val now by rememberNow()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = { RefreshButton() },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = lineup.status == LineupStore.Status.Loading,
            onRefresh = { scope.launch { lineup.refresh() } },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (lineup.isEmpty) {
                EmptyState(
                    icon = Icons.Filled.WifiOff,
                    title = stringResource(R.string.no_lineup_yet),
                    description = stringResource(R.string.download_schedule_hint),
                )
                return@PullToRefreshBox
            }

            val playing = lineup.nowPlaying(now)
            val next = lineup.upNext(now)
            val nextAutograph = lineup.nextFavoriteAutograph(autographFavorites.ids, now)
            val favoriteSlots = lineup.upcomingFavoriteSlots(favorites.ids, now)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                // Now
                item { SectionHeader(stringResource(R.string.now)) }
                if (playing.isEmpty()) {
                    item { SecondaryText(stringResource(R.string.nothing_on_stage)) }
                } else {
                    items(playing, key = { "now-${it.id}" }) { SlotRow(it, emphasized = true) }
                }

                // Up next (only if non-empty)
                if (next.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.up_next)) }
                    items(next, key = { "next-${it.id}" }) { SlotRow(it, emphasized = false) }
                }

                // Your next autograph session — single soonest favorited session; hidden when none (§7)
                if (nextAutograph != null) {
                    item { SectionHeader(stringResource(R.string.next_autograph)) }
                    item(key = "autograph-${nextAutograph.id}") { AutographHomeRow(nextAutograph) }
                }

                // Your bands
                item { SectionHeader(stringResource(R.string.your_bands)) }
                when {
                    favorites.ids.isEmpty() ->
                        item { SecondaryText(stringResource(R.string.tap_star_hint)) }
                    favoriteSlots.isEmpty() ->
                        item { SecondaryText(stringResource(R.string.no_upcoming_favorites)) }
                    else ->
                        items(favoriteSlots, key = { "fav-${it.id}" }) {
                            SlotRow(it, emphasized = false, showDay = true)
                        }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SecondaryText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
