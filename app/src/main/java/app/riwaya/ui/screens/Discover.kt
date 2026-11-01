package app.riwaya.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.data.FeedItem
import app.riwaya.engine.Health
import app.riwaya.engine.Search
import app.riwaya.engine.SourceCatalog
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import app.riwaya.ui.ScreenModel
import app.riwaya.ui.components.Cover
import app.riwaya.ui.components.EmptyState
import app.riwaya.ui.components.Loading
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.RIconButton
import app.riwaya.ui.components.Sym
import app.riwaya.ui.components.TopBar
import app.riwaya.ui.components.glass
import app.riwaya.ui.components.glowBackground
import app.riwaya.ui.model
import app.riwaya.ui.theme.LocalRiwaya
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** One source's lists: its listings (popular, latest...) or a search, page after page. */
class BrowseModel(val sourceId: Long) : ScreenModel() {
    val items = MutableStateFlow<List<MangaInfo>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<Health.Reason?>(null)
    val listing = MutableStateFlow(0)
    val query = MutableStateFlow("")
    private var page = 1
    private var hasNext = true
    private var job: Job? = null

    fun reset() {
        job?.cancel()
        items.value = emptyList()
        page = 1
        hasNext = true
        error.value = null
        loadMore()
    }

    fun loadMore() {
        if (loading.value || !hasNext) return
        val source = Riwaya.extensions.source(sourceId) ?: return
        loading.value = true
        job = scope.launch {
            val start = System.currentTimeMillis()
            try {
                val q = query.value
                val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (q.isNotBlank()) {
                        source.getMangaList(Search.filtersFor(source, q), page)
                    } else {
                        val listings: List<Listing> = source.getListings()
                        source.getMangaList(listings.getOrNull(listing.value) ?: listings.firstOrNull(), page)
                    }
                }
                val known = items.value.map { it.key }.toSet()
                val fresh = res.mangas.filter { it.key !in known && it.title.isNotBlank() }
                items.value = items.value + fresh
                hasNext = res.hasNextPage && fresh.isNotEmpty()
                page++
                Riwaya.health.record(sourceId, true, System.currentTimeMillis() - start, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error.value = Health.reasonOf(e)
                Riwaya.health.record(sourceId, false, System.currentTimeMillis() - start, e)
                hasNext = false
            } finally {
                loading.value = false
            }
        }
    }
}

@Composable
fun DiscoverScreen() {
    val c = LocalRiwaya.current
    val installed by Riwaya.extensions.installed.collectAsState()
    val disabled by Riwaya.settings.disabledSources.flow.collectAsState()
    val langs by Riwaya.settings.languages.flow.collectAsState()
    val pinned by Riwaya.settings.pinnedSources.flow.collectAsState()
    val enabled = installed.filter { it.source.id.toString() !in disabled }
    val languages = enabled.map { it.lang }.distinct().sortedWith(compareBy({ it !in langs }, { it }))
    var lang by remember { mutableStateOf(languages.firstOrNull() ?: "ar") }
    val forLang = enabled.filter { it.lang == lang }.sortedBy { it.source.id.toString() !in pinned }
    var selected by remember { mutableStateOf<Long?>(null) }
    val current = forLang.firstOrNull { it.source.id == selected } ?: forLang.firstOrNull()

    Column(Modifier.fillMaxSize().glowBackground()) {
        TopBar(stringResource(R.string.tab_discover), onBack = null, actions = {
            RIconButton(R.drawable.ms_search, stringResource(R.string.search)) { Nav.open(Route.Search()) }
            RIconButton(R.drawable.ms_extension, stringResource(R.string.sources)) { Nav.open(Route.Sources) }
        })
        if (enabled.isEmpty()) {
            EmptyState(R.drawable.ms_extension, stringResource(R.string.home_no_sources), stringResource(R.string.home_no_sources_body), action = stringResource(R.string.sources)) { Nav.open(Route.Sources) }
            return
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(languages) { l -> Pill(SourceCatalog.languageName(l), selected = l == lang, icon = R.drawable.ms_language) { lang = l; selected = null } }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(forLang, key = { it.pkg }) { e ->
                Pill(e.displayName, selected = e.source.id == current?.source?.id) { selected = e.source.id }
            }
        }
        if (current != null) {
            BrowseContent(current.source.id, Modifier.weight(1f))
        }
    }
}

@Composable
fun SourceBrowseScreen(route: Route.SourceBrowse) {
    val ext = Riwaya.extensions.ext(route.sourceId)
    Column(Modifier.fillMaxSize().glowBackground()) {
        TopBar(ext?.displayName ?: stringResource(R.string.source_missing), onBack = { Nav.back() }, subtitle = ext?.let { if (it.displayName != it.name) it.name else null })
        BrowseContent(route.sourceId, Modifier.weight(1f), route.query)
    }
}

@Composable
fun BrowseContent(sourceId: Long, modifier: Modifier = Modifier, initialQuery: String = "") {
    val c = LocalRiwaya.current
    val m = model("browse$sourceId") { BrowseModel(sourceId).also { it.query.value = initialQuery } }
    val source = Riwaya.extensions.source(sourceId)
    val items by m.items.collectAsState()
    val loading by m.loading.collectAsState()
    val error by m.error.collectAsState()
    val listingIndex by m.listing.collectAsState()
    var grid by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf(m.query.value) }
    val state = rememberLazyGridState()

    LaunchedEffect(sourceId) { if (m.items.value.isEmpty()) m.reset() }
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(state) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { m.loadMore() }
    }

    Column(modifier) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.search_in_source), fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Sym(R.drawable.ms_search, tint = c.mute, size = 20.dp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { m.query.value = text.trim(); m.reset() }),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.glass, unfocusedContainerColor = c.glass,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.amber,
                ),
                modifier = Modifier.weight(1f).height(52.dp),
            )
            RIconButton(if (grid) R.drawable.ms_view_list else R.drawable.ms_grid_view, stringResource(R.string.toggle_layout)) { grid = !grid }
        }
        val listings = source?.getListings().orEmpty()
        if (listings.size > 1 && m.query.value.isBlank()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listings.size) { i ->
                    Pill(listingName(listings[i].name), selected = i == listingIndex) {
                        m.listing.value = i
                        m.reset()
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (m.query.value.isNotBlank()) {
            Row(Modifier.padding(horizontal = 16.dp)) {
                Pill(m.query.value, selected = true, icon = R.drawable.ms_close) { text = ""; m.query.value = ""; m.reset() }
            }
            Spacer(Modifier.height(6.dp))
        }
        LazyVerticalGrid(
            columns = if (grid) GridCells.Fixed(3) else GridCells.Fixed(1),
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items.distinctBy { it.key }, key = { _, it -> it.key }) { _, n ->
                val item = FeedItem(sourceId, n.key, n.title, n.cover)
                if (grid) {
                    GridCard(item)
                } else {
                    ListCard(item)
                }
            }
            if (loading) item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
            if (!loading && error != null && items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SourceError(sourceId, error!!) { m.reset() }
                }
            }
            if (!loading && error == null && items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(R.drawable.ms_search, stringResource(R.string.nothing_found)) }
            }
        }
    }
}

@Composable
fun listingName(name: String): String = when (name.lowercase()) {
    "popular", "most popular", "الأكثر شعبية" -> stringResource(R.string.listing_popular)
    "latest", "latest update", "latest updates", "new", "الأحدث" -> stringResource(R.string.listing_latest)
    else -> name
}

@Composable
fun GridCard(item: FeedItem) {
    val c = LocalRiwaya.current
    val title = translatedTitle(item.title)
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { Nav.open(Route.Novel(item.sourceId, item.key, item.title, item.cover)) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Cover(item.cover, title, Modifier.fillMaxWidth().aspectRatio(0.71f))
        Text(title, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
    }
}

@Composable
fun ListCard(item: FeedItem, subtitle: String? = null) {
    val c = LocalRiwaya.current
    val title = translatedTitle(item.title)
    Row(
        Modifier.fillMaxWidth().glass().clickable { Nav.open(Route.Novel(item.sourceId, item.key, item.title, item.cover)) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(item.cover, title, Modifier.size(52.dp, 72.dp), RoundedCornerShape(9.dp), showTitle = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (title != item.title) Text(item.title, color = c.mute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = c.mute, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** What went wrong with a source, and the way out (open the site to pass its check, or try again). */
@Composable
fun SourceError(sourceId: Long, reason: Health.Reason, retry: () -> Unit) {
    val ext = Riwaya.extensions.ext(sourceId)
    val site = ext?.let { SourceCatalog.meta(it.pkg)?.site } ?: (ext?.source as? ireader.core.source.HttpSource)?.baseUrl
    val body = when (reason) {
        Health.Reason.CHALLENGE -> stringResource(R.string.health_challenge)
        Health.Reason.TIMEOUT -> stringResource(R.string.health_timeout)
        Health.Reason.NETWORK -> stringResource(R.string.health_network)
        else -> stringResource(R.string.health_error)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        EmptyState(R.drawable.ms_error, stringResource(R.string.source_failed), body, action = stringResource(R.string.retry), onAction = retry)
        if (site != null) {
            Box(Modifier.padding(top = 4.dp)) {
                Pill(stringResource(R.string.open_in_browser), icon = R.drawable.ms_open_in_browser) { Nav.open(Route.Web(site)) }
            }
        }
    }
}
