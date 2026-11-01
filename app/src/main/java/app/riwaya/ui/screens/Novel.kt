package app.riwaya.ui.screens

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.data.Jobs
import app.riwaya.data.SavedNovel
import app.riwaya.engine.Content
import app.riwaya.engine.Health
import app.riwaya.listen.Player
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import app.riwaya.ui.ScreenModel
import app.riwaya.ui.components.AccentButton
import app.riwaya.ui.components.Cover
import app.riwaya.ui.components.GhostButton
import app.riwaya.ui.components.Loading
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.RIconButton
import app.riwaya.ui.components.Sym
import app.riwaya.ui.model
import app.riwaya.ui.theme.LocalRiwaya
import coil3.compose.AsyncImage
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NovelModel(val route: Route.Novel) : ScreenModel() {
    val id = SavedNovel.idOf(route.sourceId, route.key)
    val details = MutableStateFlow<MangaInfo?>(Riwaya.library.get(route.sourceId, route.key)?.toInfo())
    val chapters = MutableStateFlow<List<ChapterInfo>>(emptyList())
    val loadingDetails = MutableStateFlow(false)
    val loadingChapters = MutableStateFlow(false)
    val error = MutableStateFlow<Health.Reason?>(null)
    val translatedDescription = MutableStateFlow<String?>(null)
    val translatedTitle = MutableStateFlow<String?>(null)

    init {
        load()
    }

    fun load() {
        val source = Riwaya.extensions.source(route.sourceId)
        val lang = Riwaya.extensions.ext(route.sourceId)?.lang.orEmpty()
        scope.launch {
            Riwaya.library.cachedChapters(id)?.let { if (chapters.value.isEmpty()) chapters.value = it }
            if (source == null) return@launch
            loadingDetails.value = true
            error.value = null
            try {
                val info = withContext(Dispatchers.IO) { source.getMangaDetails(details.value ?: MangaInfo(key = route.key, title = route.title, cover = route.cover), emptyList()) }
                val fixed = info.copy(key = route.key, title = info.title.ifBlank { route.title }, cover = info.cover.ifBlank { route.cover })
                details.value = fixed
                Riwaya.library.remember(route.sourceId, fixed, lang)
                translate(fixed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error.value = Health.reasonOf(e)
                if (details.value == null) details.value = MangaInfo(key = route.key, title = route.title, cover = route.cover)
                Riwaya.library.remember(route.sourceId, details.value!!, lang)
            } finally {
                loadingDetails.value = false
            }
        }
        scope.launch {
            if (source == null) return@launch
            loadingChapters.value = true
            try {
                val list = withContext(Dispatchers.IO) {
                    source.getChapterList(details.value ?: MangaInfo(key = route.key, title = route.title), emptyList())
                }.filter { it.key.isNotBlank() }
                if (list.isNotEmpty()) {
                    chapters.value = list
                    Riwaya.library.cacheChapters(id, list)
                    Riwaya.library.setChapterCount(route.sourceId, route.key, list.size, markNew = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (chapters.value.isEmpty()) error.value = Health.reasonOf(e)
            } finally {
                loadingChapters.value = false
            }
        }
    }

    private suspend fun translate(info: MangaInfo) {
        val tr = Riwaya.translator
        if (tr.target() != "ar") return
        if (!Content.isArabic(info.title)) translatedTitle.value = tr.translateShort(info.title)
        if (info.description.isNotBlank() && !Content.isArabic(info.description)) translatedDescription.value = tr.translateShort(info.description.take(4000))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelScreen(route: Route.Novel) {
    val c = LocalRiwaya.current
    val context = LocalContext.current
    val m = model(route) { NovelModel(route) }
    val details by m.details.collectAsState()
    val chapters by m.chapters.collectAsState()
    val loadingDetails by m.loadingDetails.collectAsState()
    val loadingChapters by m.loadingChapters.collectAsState()
    val error by m.error.collectAsState()
    val trTitle by m.translatedTitle.collectAsState()
    val trDesc by m.translatedDescription.collectAsState()
    val novels by Riwaya.library.novels.collectAsState()
    val progress by Riwaya.library.progress.collectAsState()
    val jobs by Jobs.jobs.collectAsState()
    val saved = novels[m.id]
    val ext = Riwaya.extensions.ext(route.sourceId)
    var showOriginalDesc by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(saved?.newChapters) { if ((saved?.newChapters ?: 0) > 0) Riwaya.library.clearNew(route.sourceId, route.key) }

    val title = details?.title ?: route.title
    val cover = details?.cover?.ifBlank { route.cover } ?: route.cover
    val readStates = progress[m.id].orEmpty()
    val downloaded = remember(chapters, jobs) { Riwaya.library.downloadedKeys(m.id) }
    val translatedSet = remember(chapters, jobs) { Riwaya.translator.cachedKeys(m.id) }
    val shown = remember(chapters, filter, newestFirst) {
        val indexed = chapters.withIndex().toList().filter { filter.isBlank() || it.value.name.contains(filter, true) }
        if (newestFirst) indexed.reversed() else indexed
    }
    val continueIndex = saved?.lastChapterKey?.let { k -> chapters.indexOfFirst { it.key == k } }?.takeIf { it >= 0 }

    fun openChapter(i: Int) {
        val ch = chapters.getOrNull(i) ?: return
        Nav.open(Route.Reader(route.sourceId, route.key, ch.key))
    }

    fun current(): SavedNovel = Riwaya.library.get(route.sourceId, route.key)
        ?: SavedNovel(route.sourceId, route.key, title, cover, lang = ext?.lang.orEmpty())

    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(330.dp)) {
                if (cover.isNotBlank()) {
                    AsyncImage(
                        cover, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier),
                        alpha = 0.55f,
                    )
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.3f), c.bg.copy(alpha = 0.6f), c.bg))))
                Row(Modifier.statusBarsPadding().fillMaxWidth().padding(4.dp)) {
                    RIconButton(R.drawable.ms_arrow_back, stringResource(R.string.back)) { Nav.back() }
                    Spacer(Modifier.weight(1f))
                    RIconButton(R.drawable.ms_spellcheck, stringResource(R.string.glossary)) { Nav.open(Route.Glossary(route.sourceId, route.key, title)) }
                    Box {
                        RIconButton(R.drawable.ms_more_vert, stringResource(R.string.more)) { menu = true }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.download_unread)) }, leadingIcon = { Sym(R.drawable.ms_download) }, onClick = {
                                menu = false
                                Jobs.run(current(), chapters.filter { readStates[it.key]?.read != true && !Riwaya.library.isDownloaded(m.id, it.key) }, translate = false)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.translate_all_downloaded)) }, leadingIcon = { Sym(R.drawable.ms_translate) }, onClick = {
                                menu = false
                                Jobs.run(current(), chapters.filter { Riwaya.library.isDownloaded(m.id, it.key) }, translate = true)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.forget_translations)) }, leadingIcon = { Sym(R.drawable.ms_delete) }, onClick = {
                                menu = false
                                Riwaya.translator.forgetNovel(m.id)
                            })
                            val site = (ext?.source as? ireader.core.source.HttpSource)?.let { s -> if (route.key.startsWith("http")) route.key else s.baseUrl + route.key }
                                ?: route.key.takeIf { it.startsWith("http") }
                            if (site != null) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.open_in_browser)) }, leadingIcon = { Sym(R.drawable.ms_open_in_browser) }, onClick = {
                                    menu = false
                                    Nav.open(Route.Web(site))
                                })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.refresh)) }, leadingIcon = { Sym(R.drawable.ms_refresh) }, onClick = {
                                menu = false
                                m.load()
                            })
                        }
                    }
                }
                Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Cover(cover, title, Modifier.size(112.dp, 158.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(trTitle ?: title, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (trTitle != null && trTitle != title) Text(title, color = c.mute, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val author = details?.author.orEmpty()
                        val status = statusText(details?.status ?: 0)
                        Text(listOfNotNull(author.ifBlank { null }, status).joinToString(" · "), color = c.text, fontSize = 12.sp)
                        if (ext != null) Pill(ext.displayName, icon = R.drawable.ms_extension)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton(
                        if (continueIndex != null) stringResource(R.string.continue_chapter, chapters[continueIndex].name.take(18)) else stringResource(R.string.read),
                        Modifier.weight(1f),
                        icon = R.drawable.ms_menu_book_fill1,
                        enabled = chapters.isNotEmpty(),
                    ) { openChapter(continueIndex ?: 0) }
                    GhostButton(stringResource(R.string.listen), Modifier.weight(0.7f), icon = R.drawable.ms_headphones) {
                        if (chapters.isNotEmpty()) {
                            val i = continueIndex ?: 0
                            val para = readStates[chapters[i].key]?.paragraph ?: 0
                            Player.start(current(), chapters, i, para)
                            Nav.open(Route.ListenFull)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(stringResource(R.string.translate_next_n, 10), Modifier.weight(1f), icon = R.drawable.ms_translate, height = 40.dp) {
                        val from = (continueIndex ?: -1) + 1
                        Jobs.run(current(), chapters.drop(from).take(10), translate = true)
                    }
                    val inLib = saved?.inLibrary == true
                    GhostButton(
                        stringResource(if (inLib) R.string.in_library else R.string.add_library),
                        Modifier.weight(1f),
                        icon = if (inLib) R.drawable.ms_favorite_fill1 else R.drawable.ms_favorite,
                        iconTint = if (inLib) c.coral else null,
                        height = 40.dp,
                    ) {
                        if (saved == null) Riwaya.library.remember(route.sourceId, details ?: MangaInfo(key = route.key, title = title, cover = cover), ext?.lang.orEmpty())
                        Riwaya.library.setInLibrary(route.sourceId, route.key, !inLib)
                        if (!inLib) Riwaya.library.setChapterCount(route.sourceId, route.key, chapters.size, markNew = false)
                    }
                }
                val desc = if (showOriginalDesc) details?.description.orEmpty() else trDesc ?: details?.description.orEmpty()
                if (desc.isNotBlank()) {
                    Column(Modifier.animateContentSize()) {
                        Text(desc, color = c.mute, fontSize = 13.sp, lineHeight = 21.sp, maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { expanded = !expanded })
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(if (expanded) R.string.less else R.string.more_text), color = c.amber, fontSize = 12.sp, modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp))
                            if (trDesc != null) Text(stringResource(if (showOriginalDesc) R.string.show_translation else R.string.show_original), color = c.teal, fontSize = 12.sp, modifier = Modifier.clickable { showOriginalDesc = !showOriginalDesc }.padding(vertical = 4.dp))
                        }
                    }
                }
                val genres = details?.genres.orEmpty()
                if (genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        genres.take(10).forEach { g -> Pill(g) { Nav.open(Route.Search(g)) } }
                    }
                }
                Pill(stringResource(R.string.other_sources), icon = R.drawable.ms_layers, teal = true) { Nav.open(Route.Search(title)) }
                if (error != null && chapters.isEmpty() && !loadingChapters) {
                    SourceError(route.sourceId, error!!) { m.load() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chapters_count, chapters.size), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                    RIconButton(R.drawable.ms_sort, stringResource(R.string.sort)) { newestFirst = !newestFirst }
                }
                if (chapters.size > 20) {
                    TextField(
                        value = filter, onValueChange = { filter = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_chapters), fontSize = 13.sp) },
                        leadingIcon = { Sym(R.drawable.ms_search, tint = c.mute, size = 20.dp) },
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = c.glass, unfocusedContainerColor = c.glass,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.amber,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    )
                }
                if (loadingChapters && chapters.isEmpty()) Loading()
            }
        }
        items(shown, key = { "${it.index}|${it.value.key}" }) { (i, ch) ->
            val state = readStates[ch.key]
            val read = state?.read == true
            val isDownloaded = app.riwaya.core.sha1(ch.key) in downloaded
            val isTranslated = Riwaya.translator.cacheHash(ch.key) in translatedSet
            Row(
                Modifier.fillMaxWidth().clickable { openChapter(i) }.padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (ch.number >= 0) ch.number.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } else (i + 1).toString(),
                    color = c.mute, fontSize = 12.sp, modifier = Modifier.width(44.dp),
                )
                Text(ch.name, color = if (read) c.mute else c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (state != null && !read && state.fraction > 0.02f) {
                    Text("${(state.fraction * 100).toInt()}%", color = c.amber, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
                if (isTranslated) Sym(R.drawable.ms_translate, tint = c.teal, size = 17.dp, modifier = Modifier.padding(horizontal = 3.dp))
                if (isDownloaded) Sym(R.drawable.ms_download_done, tint = c.amber, size = 17.dp, modifier = Modifier.padding(horizontal = 3.dp))
                if (read) Sym(R.drawable.ms_done_all, tint = c.mute, size = 17.dp, modifier = Modifier.padding(horizontal = 3.dp))
            }
        }
        if (loadingDetails && details?.description.isNullOrBlank()) item { Loading() }
    }
}

@Composable
fun statusText(status: Long): String? = when (status) {
    MangaInfo.ONGOING -> stringResource(R.string.status_ongoing)
    MangaInfo.COMPLETED, MangaInfo.PUBLISHING_FINISHED -> stringResource(R.string.status_completed)
    MangaInfo.ON_HIATUS -> stringResource(R.string.status_hiatus)
    MangaInfo.CANCELLED -> stringResource(R.string.status_cancelled)
    else -> null
}
