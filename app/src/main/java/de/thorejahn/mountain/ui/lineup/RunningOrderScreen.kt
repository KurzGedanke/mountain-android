package de.thorejahn.mountain.ui.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.thorejahn.mountain.R
import de.thorejahn.mountain.domain.LineupStore
import de.thorejahn.mountain.ui.LocalFavoritesStore
import de.thorejahn.mountain.ui.LocalLineupStore
import de.thorejahn.mountain.ui.common.EmptyState
import de.thorejahn.mountain.ui.common.Fmt
import de.thorejahn.mountain.ui.common.RefreshButton
import de.thorejahn.mountain.ui.common.ScheduleRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningOrderScreen(modifier: Modifier = Modifier) {
    val lineup = LocalLineupStore.current
    val favorites = LocalFavoritesStore.current
    val scope = rememberCoroutineScope()

    var favoritesOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lineup_title)) },
                navigationIcon = {
                    IconButton(onClick = { favoritesOnly = !favoritesOnly }) {
                        Icon(
                            imageVector = if (favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(R.string.favorites_only),
                        )
                    }
                },
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
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.no_lineup_yet),
                    description = stringResource(R.string.download_schedule_hint),
                )
                return@PullToRefreshBox
            }

            val trimmed = query.trim()
            val visibleSlots = lineup.slots
                .let { if (favoritesOnly) it.filter { s -> favorites.isFavorite(s.bandId) } else it }
                .let {
                    if (trimmed.isEmpty()) it
                    else it.filter { s -> s.band.contains(trimmed, ignoreCase = true) }
                }

            val days = visibleSlots
                .groupBy { Fmt.startOfDay(it.start) }
                .toSortedMap()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.search_bands)) },
                    )
                }

                if (visibleSlots.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                            if (trimmed.isNotEmpty()) {
                                Text(
                                    text = "“$trimmed”",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                EmptyStateInline(
                                    title = stringResource(R.string.no_favorites),
                                    description = stringResource(R.string.star_to_see_here),
                                )
                            }
                        }
                    }
                } else {
                    days.forEach { (day, slots) ->
                        item(key = "day-$day") {
                            Text(
                                text = Fmt.day(day),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(slots.sortedBy { it.start }, key = { it.id }) { ScheduleRow(it) }
                    }

                    val updatedAt = lineup.updatedAt
                    if (updatedAt != null) {
                        item(key = "updated") {
                            Text(
                                text = stringResource(R.string.updated, Fmt.relative(updatedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateInline(title: String, description: String) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.StarBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
