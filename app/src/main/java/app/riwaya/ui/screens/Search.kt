package app.riwaya.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import app.riwaya.engine.SourceResult
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import app.riwaya.ui.ScreenModel
import app.riwaya.ui.components.Cover
import app.riwaya.ui.components.HealthDot
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.RIconButton
import app.riwaya.ui.components.Sym
import app.riwaya.ui.components.glass
import app.riwaya.ui.components.glowBackground
import app.riwaya.ui.model
import app.riwaya.ui.theme.LocalRiwaya
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchModel : ScreenModel() {
    val results = MutableStateFlow<List<SourceResult>>(emptyList())
    val running = MutableStateFlow(false)
    var lastQuery = ""
    private var job: Job? = null

    fun search(query: String, lang: String?) {
        val q = query.trim()
        if (q.isEmpty()) return
        lastQuery = q
        job?.cancel()
        val exts = Riwaya.extensions.enabled().filter { lang == null || it.lang == lang }
            .filter { Riwaya.health.checks.value[it.source.id]?.status != Health.Status.NO_SEARCH }
        results.value = exts.map { SourceResult(it) }
        running.value = true
        job = scope.launch {
            Search.global(exts, q, Riwaya.health).collect { r ->
                results.value = results.value.map { if (it.ext.pkg == r.ext.pkg) r else it }
            }
            running.value = false
        }
    }
}

@Composable
fun SearchScreen(route: Route.Search) {
    val c = LocalRiwaya.current
    val m = model(route) { SearchModel() }
    val results by m.results.collectAsState()
    var text by remember { mutableStateOf(route.query) }
    var lang by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val langs = Riwaya.extensions.enabled().map { it.lang }.distinct()

    LaunchedEffect(Unit) {
        if (route.query.isNotBlank() && m.results.value.isEmpty()) m.search(route.query, null) else if (route.query.isBlank()) runCatching { focus.requestFocus() }
    }

    val done = results.count { it.state != SourceResult.State.LOADING }
    val merged = remember(results) { Search.merge(results) }
    val ordered = remember(results) {
        results.sortedWith(compareBy({ it.state != SourceResult.State.DONE }, { it.state == SourceResult.State.LOADING }, { -it.novels.size }))
    }

    Column(Modifier.fillMaxSize().glowBackground().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RIconButton(R.drawable.ms_arrow_back, stringResource(R.string.back)) { Nav.back() }
            TextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_all_hint), fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { m.search(text, lang) }),
                trailingIcon = { if (text.isNotEmpty()) RIconButton(R.drawable.ms_close, stringResource(R.string.clear), c.mute) { text = "" } },
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.glass, unfocusedContainerColor = c.glass,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.amber,
                ),
                modifier = Modifier.weight(1f).height(54.dp).focusRequester(focus),
            )
        }
        if (langs.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Pill(stringResource(R.string.all), selected = lang == null) { lang = null; if (m.lastQuery.isNotEmpty()) m.search(m.lastQuery, null) } }
                items(langs) { l -> Pill(app.riwaya.engine.SourceCatalog.languageName(l), selected = lang == l) { lang = l; if (m.lastQuery.isNotEmpty()) m.search(m.lastQuery, l) } }
            }
        }
        if (results.isNotEmpty()) {
            Text(
                stringResource(R.string.search_status, results.size, results.count { it.state == SourceResult.State.DONE }, results.size - done, results.count { it.state == SourceResult.State.TIMEOUT }),
                color = c.mute, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(merged.take(5)) { mn ->
                Column(Modifier.fillMaxWidth().glass().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.same_novel_sources, mn.hits.size), icon = R.drawable.ms_layers, teal = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cover(mn.cover, mn.title, Modifier.size(52.dp, 72.dp), RoundedCornerShape(9.dp), showTitle = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(translatedTitle(mn.title), color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(mn.hits.joinToString(" · ") { it.first.displayName }, color = c.mute, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(mn.hits) { (ext, info) ->
                            Pill(ext.displayName, icon = R.drawable.ms_menu_book) { Nav.open(Route.Novel(ext.source.id, info.key, info.title, info.cover)) }
                        }
                    }
                }
            }
            items(ordered, key = { it.ext.pkg }) { r ->
                Column(Modifier.fillMaxWidth().glass().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (r.state) {
                            SourceResult.State.LOADING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = c.amber)
                            SourceResult.State.DONE, SourceResult.State.EMPTY -> HealthDot(Health.Status.WORKING)
                            else -> HealthDot(Health.Status.BROKEN)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(r.ext.displayName, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f).clickable { Nav.open(Route.SourceBrowse(r.ext.source.id, m.lastQuery)) })
                        Text(
                            when (r.state) {
                                SourceResult.State.LOADING -> stringResource(R.string.searching)
                                SourceResult.State.TIMEOUT -> stringResource(R.string.timeout_8s)
                                SourceResult.State.FAILED -> stringResource(R.string.source_failed)
                                SourceResult.State.EMPTY -> stringResource(R.string.nothing_found)
                                SourceResult.State.DONE -> stringResource(R.string.seconds, r.ms / 1000f)
                            },
                            color = c.mute, fontSize = 11.sp,
                        )
                    }
                    if (r.novels.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(r.novels.distinctBy { it.key }.take(20), key = { it.key }) { n -> SmallCard(FeedItem(r.ext.source.id, n.key, n.title, n.cover), width = 88.dp) }
                        }
                    }
                }
            }
        }
    }
}
