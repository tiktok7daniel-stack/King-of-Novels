package app.riwaya.ui.screens

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.MainActivity
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.data.Jobs
import app.riwaya.data.SavedNovel
import app.riwaya.engine.Content
import app.riwaya.engine.Health
import app.riwaya.listen.Player
import app.riwaya.translate.Engine
import app.riwaya.translate.TranslationProgress
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import app.riwaya.ui.ScreenModel
import app.riwaya.ui.components.EmptyState
import app.riwaya.ui.components.GhostButton
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.RSwitch
import app.riwaya.ui.components.SheetShape
import app.riwaya.ui.model
import app.riwaya.ui.theme.Fonts
import app.riwaya.ui.theme.pageColors
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderModel(val route: Route.Reader) : ScreenModel() {
    val novelId = SavedNovel.idOf(route.sourceId, route.key)
    val chapters = MutableStateFlow<List<ChapterInfo>>(emptyList())
    val index = MutableStateFlow(-1)
    val original = MutableStateFlow<List<String>?>(null)
    val translation = MutableStateFlow<TranslationProgress?>(null)
    val needsTranslation = MutableStateFlow(false)
    val error = MutableStateFlow<Health.Reason?>(null)
    val restoreTo = MutableStateFlow(0)
    private var translateJob: Job? = null
    private var loadJob: Job? = null

    val novel: SavedNovel
        get() = Riwaya.library.get(route.sourceId, route.key)
            ?: SavedNovel(route.sourceId, route.key, "", lang = Riwaya.extensions.ext(route.sourceId)?.lang.orEmpty())

    init {
        scope.launch {
            val list = Riwaya.library.cachedChapters(novelId) ?: run {
                val source = Riwaya.extensions.source(route.sourceId)
                runCatching { withContext(Dispatchers.IO) { source?.getChapterList(MangaInfo(key = route.key, title = novel.title), emptyList()) } }.getOrNull().orEmpty()
            }
            chapters.value = list
            open(list.indexOfFirst { it.key == route.chapterKey }.coerceAtLeast(0))
        }
    }

    fun open(i: Int) {
        val ch = chapters.value.getOrNull(i) ?: return
        index.value = i
        original.value = null
        translation.value = null
        error.value = null
        translateJob?.cancel()
        loadJob?.cancel()
        restoreTo.value = Riwaya.library.chapterState(novelId, ch.key)?.takeIf { !it.read }?.paragraph ?: 0
        loadJob = scope.launch {
            try {
                val text = Riwaya.library.cachedText(novelId, ch.key) ?: run {
                    val source = Riwaya.extensions.source(route.sourceId) ?: error("Source not installed")
                    withContext(Dispatchers.IO) { Content.fetch(source, ch) }.also { if (it.isNotEmpty()) Riwaya.library.cacheText(novelId, ch.key, it) }
                }
                if (text.isEmpty()) error("Empty chapter")
                original.value = text
                needsTranslation.value = Jobs.needsTranslation(novel, text)
                if (needsTranslation.value) {
                    val cached = Riwaya.translator.cached(novelId, ch.key)
                    if (cached != null && cached.size == text.size) {
                        translation.value = TranslationProgress(cached, cached.size, cached.size)
                    } else if (Riwaya.settings.autoTranslate.get() && Riwaya.settings.translationMode.get() != "ORIGINAL") {
                        translate()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error.value = Health.reasonOf(e)
            }
        }
    }

    fun translate(force: Boolean = false) {
        val text = original.value ?: return
        val ch = chapters.value.getOrNull(index.value) ?: return
        if (force) Riwaya.translator.forget(novelId, ch.key)
        translateJob?.cancel()
        translateJob = scope.launch {
            Riwaya.translator.translate(novelId, novel.title, ch.key, text, novel.lang).collect { translation.value = it }
        }
    }

    fun saveProgress(paragraph: Int, total: Int) {
        val ch = chapters.value.getOrNull(index.value) ?: return
        val fraction = if (total <= 1) 1f else (paragraph + 1).toFloat() / total
        Riwaya.library.saveProgress(route.sourceId, route.key, ch, index.value, fraction.coerceIn(0f, 1f), paragraph)
    }
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(route: Route.Reader) {
    val context = LocalContext.current
    val m = model(route) { ReaderModel(route) }
    val st = Riwaya.settings
    val theme by st.readerTheme.flow.collectAsState()
    val fontKey by st.readerFont.flow.collectAsState()
    val fontSize by st.fontSize.flow.collectAsState()
    val lineHeight by st.lineHeight.flow.collectAsState()
    val margin by st.margin.flow.collectAsState()
    val justify by st.justify.flow.collectAsState()
    val mode by st.translationMode.flow.collectAsState()
    val keepOn by st.keepScreenOn.flow.collectAsState()
    val volumeKeys by st.volumeKeys.flow.collectAsState()
    val chapters by m.chapters.collectAsState()
    val index by m.index.collectAsState()
    val original by m.original.collectAsState()
    val translation by m.translation.collectAsState()
    val needs by m.needsTranslation.collectAsState()
    val error by m.error.collectAsState()
    val pc = pageColors(theme)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(false) }
    var shownOriginal by remember(index) { mutableStateOf(setOf<Int>()) }
    var glossaryFor by remember { mutableStateOf<String?>(null) }
    var chrome by remember { mutableStateOf(true) }
    val chapter = chapters.getOrNull(index)

    DisposableEffect(keepOn) {
        (context as? MainActivity)?.keepScreenOn(keepOn)
        onDispose { (context as? MainActivity)?.keepScreenOn(false) }
    }
    DisposableEffect(volumeKeys) {
        MainActivity.volumePaging = volumeKeys
        onDispose { MainActivity.volumePaging = false }
    }
    LaunchedEffect(Unit) {
        MainActivity.volumeKeys.collect { dir ->
            val h = listState.layoutInfo.viewportSize.height * 0.85f
            listState.animateScrollBy(dir * h)
        }
    }

    // Back to where the chapter was left
    LaunchedEffect(index, original != null) {
        if (original != null) {
            val to = m.restoreTo.value
            listState.scrollToItem(if (to > 0) (to + 1).coerceAtMost((original?.size ?: 1)) else 0)
        }
    }
    // Progress saved a moment after scrolling stops; the next chapter is translated ahead at 60 %
    LaunchedEffect(index, original) {
        val total = original?.size ?: return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) }
            .debounce(600)
            .collect { (first, last) ->
                val p = if (last >= total) total - 1 else (first - 1).coerceIn(0, total - 1)
                m.saveProgress(p, total)
                if (needs && (p + 1).toFloat() / total >= 0.6f) Jobs.prefetch(context, m.novel, chapters, index)
            }
    }

    val shown: List<String> = original.orEmpty()
    val tr = translation
    val showTranslated = needs && mode != "ORIGINAL"

    Box(Modifier.fillMaxSize().background(pc.bg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { chrome = !chrome },
            contentPadding = PaddingValues(start = margin.dp, end = margin.dp, top = 110.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy((fontSize * 0.8f).dp),
        ) {
            item(key = "title") {
                Text(
                    chapter?.name.orEmpty(),
                    fontFamily = Fonts.display, fontWeight = FontWeight.Bold, fontSize = (fontSize + 6).sp, color = pc.text,
                    style = TextStyle(textDirection = TextDirection.Content), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (original == null && error == null) {
                item { app.riwaya.ui.components.Loading() }
            }
            if (error != null) {
                item {
                    Column {
                        SourceError(route.sourceId, error!!) { m.open(index) }
                    }
                }
            }
            itemsIndexed(shown, key = { i, _ -> "p$i" }) { i, para ->
                val translated = tr?.paragraphs?.getOrNull(i)
                val readingStyle = TextStyle(
                    fontFamily = Fonts.reading(fontKey), fontSize = fontSize.sp, lineHeight = (fontSize * lineHeight).sp,
                    color = pc.text, textAlign = if (justify) TextAlign.Justify else TextAlign.Start, textDirection = TextDirection.Content,
                )
                Column(
                    Modifier.fillMaxWidth().combinedClickable(
                        interactionSource = null, indication = null,
                        onClick = { if (showTranslated && translated != null) shownOriginal = if (i in shownOriginal) shownOriginal - i else shownOriginal + i else chrome = !chrome },
                        onLongClick = { glossaryFor = para },
                    ),
                ) {
                    when {
                        !showTranslated -> Text(para, style = readingStyle)
                        translated == null -> Text(para, style = readingStyle.copy(color = pc.text.copy(alpha = 0.38f), fontFamily = Fonts.literata, fontSize = (fontSize - 1).sp))
                        else -> {
                            Text(translated, style = readingStyle)
                            if (mode == "BILINGUAL" || i in shownOriginal) {
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.height(IntrinsicSize.Min)) {
                                    Box(Modifier.width(2.dp).fillMaxHeight().background(Color(0xFF3DD6C6).copy(alpha = 0.5f)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(para, style = readingStyle.copy(fontSize = (fontSize - 3).sp, lineHeight = ((fontSize - 3) * 1.6f).sp, color = pc.mute, fontFamily = Fonts.literata, textAlign = TextAlign.Start))
                                }
                            }
                        }
                    }
                }
            }
            if (original != null) {
                item(key = "end") {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val next = chapters.getOrNull(index + 1)
                        if (next != null) {
                            ChapterButton(stringResource(R.string.next_chapter), next.name, pc.text, pc.mute) { m.open(index + 1) }
                        } else {
                            Text(stringResource(R.string.last_chapter), color = pc.mute, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        chapters.getOrNull(index - 1)?.let { prev ->
                            ChapterButton(stringResource(R.string.prev_chapter), prev.name, pc.text, pc.mute) { m.open(index - 1) }
                        }
                    }
                }
            }
        }

        // Top: chapter, translation progress, tools
        if (chrome) {
            Column(Modifier.fillMaxWidth().background(pc.bg.copy(alpha = 0.96f)).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ReaderIcon(R.drawable.ms_arrow_back, stringResource(R.string.back), pc.text) { Nav.back() }
                    Text(chapter?.name.orEmpty(), color = pc.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (needs) ReaderIcon(R.drawable.ms_translate, stringResource(R.string.translate), if (tr != null) Color(0xFF3DD6C6) else pc.text) { if (tr == null) m.translate() else sheet = true }
                    ReaderIcon(R.drawable.ms_text_fields, stringResource(R.string.reader_settings), pc.text) { sheet = true }
                    ReaderIcon(R.drawable.ms_headphones, stringResource(R.string.listen), pc.text) {
                        if (chapters.isNotEmpty()) {
                            val first = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                            Player.start(m.novel, chapters, index, first)
                            Nav.open(Route.ListenFull)
                        }
                    }
                }
                if (needs && tr != null && !tr.finished) {
                    LinearProgressIndicator(
                        progress = { tr.done.toFloat() / tr.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(2.dp), color = Color(0xFF3DD6C6), trackColor = pc.mute.copy(alpha = 0.2f),
                    )
                }
                if (needs && mode != "ORIGINAL") {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, pc.mute.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(3.dp)) {
                        listOf("TRANSLATED" to R.string.mode_translated, "BILINGUAL" to R.string.mode_bilingual, "ORIGINAL" to R.string.mode_original).forEach { (k, label) ->
                            val on = mode == k
                            Text(
                                stringResource(label),
                                color = if (on) pc.bg else pc.text, fontSize = 11.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) pc.text else Color.Transparent).clickable { st.translationMode.set(k) }.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            // Bottom: where the reader is and how long is left
            val total = shown.size.coerceAtLeast(1)
            val at = (listState.firstVisibleItemIndex - 1).coerceIn(0, total - 1)
            val wordsLeft = shown.drop(at).sumOf { it.split(' ').size }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(pc.bg.copy(alpha = 0.96f)).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.reader_left, ((at + 1) * 100 / total), (wordsLeft / 200).coerceAtLeast(1)), color = pc.mute, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (needs && tr != null) {
                    Text(
                        if (tr.finished) engineLabel(tr.engine) else stringResource(R.string.translating_progress, tr.done, tr.total),
                        color = pc.mute, fontSize = 11.sp,
                    )
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, shape = SheetShape, containerColor = pc.bg) {
            ReaderSettings(pc.text, pc.mute, needs, onRetranslate = { m.translate(force = true); sheet = false }, onGlossary = {
                sheet = false
                Nav.open(Route.Glossary(route.sourceId, route.key, m.novel.title))
            })
        }
    }
    glossaryFor?.let { para ->
        GlossaryQuickEdit(para, onDismiss = { glossaryFor = null }) { from, to, redo ->
            val g = Riwaya.translator.readGlossary(m.novelId).toMutableMap()
            g[from] = to
            Riwaya.translator.writeGlossary(m.novelId, g)
            glossaryFor = null
            if (redo) m.translate(force = true)
        }
    }
}

@Composable
private fun engineLabel(e: Engine?): String = when (e) {
    Engine.AI -> stringResource(R.string.engine_ai)
    Engine.GOOGLE -> stringResource(R.string.engine_google)
    Engine.MLKIT -> stringResource(R.string.engine_mlkit)
    null -> stringResource(R.string.translated)
}

@Composable
private fun ReaderIcon(icon: Int, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        androidx.compose.material3.Icon(painterResource(icon), description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ChapterButton(label: String, name: String, text: Color, mute: Color, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, mute.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(label, color = mute, fontSize = 11.sp)
        Text(name, color = text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReaderSettings(text: Color, mute: Color, needs: Boolean, onRetranslate: () -> Unit, onGlossary: () -> Unit) {
    val st = Riwaya.settings
    val theme by st.readerTheme.flow.collectAsState()
    val font by st.readerFont.flow.collectAsState()
    val size by st.fontSize.flow.collectAsState()
    val lh by st.lineHeight.flow.collectAsState()
    val margin by st.margin.flow.collectAsState()
    val justify by st.justify.flow.collectAsState()
    val keepOn by st.keepScreenOn.flow.collectAsState()
    val volume by st.volumeKeys.flow.collectAsState()
    val context = LocalContext.current
    var brightness by remember { mutableStateOf((context as? Activity)?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.6f) }
    val sliderColors = SliderDefaults.colors(thumbColor = Color(0xFFF5B451), activeTrackColor = Color(0xFFF5B451), inactiveTrackColor = mute.copy(alpha = 0.3f))

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.reader_settings), color = text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("PAPER", "SEPIA", "NIGHT", "AMOLED").forEach { k ->
                val pc = pageColors(k)
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(pc.bg).border(if (theme == k) 3.dp else 1.dp, if (theme == k) Color(0xFFF5B451) else mute.copy(alpha = 0.4f), CircleShape).clickable { st.readerTheme.set(k) },
                    contentAlignment = Alignment.Center,
                ) { Text("أب", color = pc.text, fontFamily = Fonts.naskh, fontSize = 16.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("NASKH" to "نسخ", "AMIRI" to "أميري", "PLEX" to "Plex", "LITERATA" to "Literata").forEach { (k, l) ->
                Pill(l, selected = font == k) { st.readerFont.set(k) }
            }
        }
        LabeledSlider(stringResource(R.string.font_size, size.toInt()), size, 13f..30f, text, sliderColors) { st.fontSize.set(it) }
        LabeledSlider(stringResource(R.string.line_height, "%.1f".format(lh)), lh, 1.3f..2.6f, text, sliderColors) { st.lineHeight.set(it) }
        LabeledSlider(stringResource(R.string.margins, margin), margin.toFloat(), 8f..48f, text, sliderColors) { st.margin.set(it.toInt()) }
        LabeledSlider(stringResource(R.string.brightness), brightness, 0.05f..1f, text, sliderColors) {
            brightness = it
            (context as? Activity)?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = it } }
        }
        ToggleRow(stringResource(R.string.justify), justify, text) { st.justify.set(it) }
        ToggleRow(stringResource(R.string.keep_screen_on), keepOn, text) { st.keepScreenOn.set(it) }
        ToggleRow(stringResource(R.string.volume_keys), volume, text) { st.volumeKeys.set(it) }
        if (needs) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(stringResource(R.string.retranslate), Modifier.weight(1f), icon = R.drawable.ms_refresh, height = 42.dp, onClick = onRetranslate)
                GhostButton(stringResource(R.string.glossary), Modifier.weight(1f), icon = R.drawable.ms_spellcheck, height = 42.dp, onClick = onGlossary)
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: Color, colors: androidx.compose.material3.SliderColors, onChange: (Float) -> Unit) {
    Column {
        Text(label, color = text, fontSize = 13.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = colors)
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, text: Color, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        RSwitch(value, onChange)
    }
}

/** Long-press on a paragraph: give a name its fixed translation for the whole novel. */
@Composable
fun GlossaryQuickEdit(paragraph: String, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.glossary_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(paragraph.take(220), fontSize = 12.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.glossary_original)) }, singleLine = true)
                OutlinedTextField(to, { to = it }, label = { Text(stringResource(R.string.glossary_translation)) }, singleLine = true)
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = from.isNotBlank() && to.isNotBlank(), onClick = { onSave(from.trim(), to.trim(), true) }) { Text(stringResource(R.string.save_retranslate)) }
                TextButton(enabled = from.isNotBlank() && to.isNotBlank(), onClick = { onSave(from.trim(), to.trim(), false) }) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
