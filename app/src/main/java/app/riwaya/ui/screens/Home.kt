package app.riwaya.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.data.FeedItem
import app.riwaya.data.SavedNovel
import app.riwaya.engine.SourceCatalog
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import app.riwaya.ui.Tab
import app.riwaya.ui.components.Cover
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.ProgressRing
import app.riwaya.ui.components.SectionHeader
import app.riwaya.ui.components.Shimmer
import app.riwaya.ui.components.Sym
import app.riwaya.ui.components.glass
import app.riwaya.ui.components.glowBackground
import app.riwaya.ui.theme.LocalRiwaya
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val c = LocalRiwaya.current
    val feed by Riwaya.home.feed.collectAsState()
    val refreshing by Riwaya.home.refreshing.collectAsState()
    val novels by Riwaya.library.novels.collectAsState()
    val installed by Riwaya.extensions.installed.collectAsState()
    val genres by Riwaya.settings.genres.flow.collectAsState()

    LaunchedEffect(installed.size) {
        if (installed.isNotEmpty() && Riwaya.home.isStale()) {
            Riwaya.home.refresh()
            Riwaya.home.checkLibraryUpdates()
        }
    }

    val continueList = remember(novels) { novels.values.filter { it.lastChapterKey.isNotEmpty() }.sortedByDescending { it.lastReadAt }.take(10) }
    val newChapters = remember(novels) { novels.values.filter { it.inLibrary && it.newChapters > 0 }.sortedByDescending { it.newChapters } }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            Riwaya.scope.launch {
                Riwaya.home.refresh()
                Riwaya.home.checkLibraryUpdates()
            }
        },
        modifier = Modifier.fillMaxSize().glowBackground(),
    ) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(greeting()), color = c.mute, fontSize = 12.sp)
                        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, color = c.text)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).clickable { Nav.open(Route.History) }, contentAlignment = Alignment.Center) {
                        Sym(R.drawable.ms_history, description = stringResource(R.string.history))
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(46.dp).glass(RoundedCornerShape(16.dp)).clickable { Nav.open(Route.Search()) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym(R.drawable.ms_search, tint = c.mute)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.home_search_hint, Riwaya.extensions.enabled().size), color = c.mute, fontSize = 13.sp)
                }
            }
            if (continueList.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_continue), Modifier.padding(horizontal = 16.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(continueList, key = { it.id }) { n -> ContinueCard(n) }
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val ordered = Genres.all.sortedBy { it.query !in genres }
                    items(ordered) { g -> Pill(stringResource(g.label), selected = g.query in genres) { Nav.open(Route.Search(g.query)) } }
                }
            }
            item { SectionHeader(stringResource(R.string.home_recommended), Modifier.padding(horizontal = 16.dp)) }
            item {
                if (feed.recommended.isEmpty()) {
                    if (installed.isEmpty()) {
                        app.riwaya.ui.components.EmptyState(R.drawable.ms_extension, stringResource(R.string.home_no_sources), stringResource(R.string.home_no_sources_body), action = stringResource(R.string.sources)) { Nav.open(Route.Sources) }
                    } else {
                        ShimmerRow()
                    }
                } else {
                    NovelRow(feed.recommended)
                }
            }
            if (newChapters.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.home_new_chapters), Modifier.padding(horizontal = 16.dp), trailing = {
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).padding(horizontal = 4.dp)) {
                            Text(newChapters.sumOf { it.newChapters }.toString(), color = c.teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    })
                }
                items(newChapters.take(5), key = { "new" + it.id }) { n ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Nav.open(Route.Novel(n.sourceId, n.key, n.title, n.cover)) }.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(n.cover, n.title, Modifier.size(38.dp, 50.dp), RoundedCornerShape(8.dp), showTitle = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.home_new_count, n.newChapters, Riwaya.extensions.ext(n.sourceId)?.displayName.orEmpty()), color = c.mute, fontSize = 12.sp)
                        }
                        Sym(R.drawable.ms_chevron_left, tint = c.mute)
                    }
                }
            }
            feed.rows.forEach { row ->
                item(key = "row" + row.sourceId) {
                    SectionHeader(
                        stringResource(R.string.home_popular_on, row.sourceName),
                        Modifier.padding(horizontal = 16.dp),
                        action = stringResource(R.string.all),
                        trailing = { Text(SourceCatalog.languageName(row.lang), color = c.mute, fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp)) },
                    ) { Nav.open(Route.SourceBrowse(row.sourceId)) }
                }
                item(key = "rowitems" + row.sourceId) { NovelRow(row.items) }
            }
            if (feed.rows.isEmpty() && installed.isNotEmpty() && refreshing) {
                item { ShimmerRow() }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.discover_more), icon = R.drawable.ms_explore) { Nav.goTab(Tab.DISCOVER) }
                    Pill(stringResource(R.string.sources), icon = R.drawable.ms_extension) { Nav.open(Route.Sources) }
                }
            }
        }
    }
}

private fun greeting(): Int {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> R.string.good_morning
        in 12..17 -> R.string.good_afternoon
        else -> R.string.good_evening
    }
}

@Composable
private fun ContinueCard(n: SavedNovel) {
    val c = LocalRiwaya.current
    val listened = n.lastListened > 0 && n.lastListened >= n.lastReadAt - 1000
    Row(
        Modifier.width(290.dp).glass().clickable { Nav.open(Route.Novel(n.sourceId, n.key, n.title, n.cover)) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(n.cover, n.title, Modifier.size(58.dp, 82.dp), showTitle = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(n.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(n.lastChapterName, color = c.mute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (listened) Pill(stringResource(R.string.home_listening), icon = R.drawable.ms_headphones, teal = true)
        }
        Spacer(Modifier.width(8.dp))
        val total = n.totalChapters.coerceAtLeast(1)
        ProgressRing(((n.lastChapterIndex + n.lastFraction) / total).coerceIn(0f, 1f))
    }
}

@Composable
fun NovelRow(items: List<FeedItem>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items.distinctBy { it.sourceId.toString() + it.key }, key = { it.sourceId.toString() + it.key }) { item -> SmallCard(item) }
    }
}

@Composable
fun SmallCard(item: FeedItem, width: androidx.compose.ui.unit.Dp = 104.dp) {
    val c = LocalRiwaya.current
    val title = translatedTitle(item.title)
    Column(Modifier.width(width).clip(RoundedCornerShape(12.dp)).clickable { Nav.open(Route.Novel(item.sourceId, item.key, item.title, item.cover)) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Cover(item.cover, title, Modifier.fillMaxWidth().height(width * 1.4f))
        Text(title, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        if (item.sourceName.isNotEmpty()) Text(item.sourceName, color = c.mute, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShimmerRow() {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(5) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Shimmer(Modifier.size(104.dp, 146.dp))
                Shimmer(Modifier.size(80.dp, 10.dp))
            }
        }
    }
}

/** A title in the reader's language when the setting is on (kept once translated). */
@Composable
fun translatedTitle(title: String): String {
    val on by Riwaya.settings.translateTitles.flow.collectAsState()
    if (!on) return title
    val tr = Riwaya.translator
    var shown by remember(title) { mutableStateOf(tr.cachedShort(title) ?: title) }
    LaunchedEffect(title) {
        if (shown == title && !app.riwaya.engine.Content.isArabic(title) && tr.target() == "ar") {
            shown = tr.translateShort(title)
        }
    }
    return shown
}
