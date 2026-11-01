package app.riwaya.ui.screens

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.riwaya.BuildConfig
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.core.Net
import app.riwaya.data.JobState
import app.riwaya.data.Jobs
import app.riwaya.translate.Engine
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import app.riwaya.ui.components.Cover
import app.riwaya.ui.components.EmptyState
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.RIconButton
import app.riwaya.ui.components.RSwitch
import app.riwaya.ui.components.SectionHeader
import app.riwaya.ui.components.SettingRow
import app.riwaya.ui.components.Sym
import app.riwaya.ui.components.TopBar
import app.riwaya.ui.components.glass
import app.riwaya.ui.components.glowBackground
import app.riwaya.ui.theme.LocalRiwaya
import kotlinx.coroutines.launch

@Composable
fun MoreScreen() {
    val c = LocalRiwaya.current
    val jobs by Jobs.jobs.collectAsState()
    val active = jobs.count { !it.finished }
    Column(Modifier.fillMaxSize().glowBackground().verticalScroll(rememberScrollState())) {
        TopBar(stringResource(R.string.tab_more), onBack = null)
        Column(Modifier.padding(16.dp).glass()) {
            SettingRow(stringResource(R.string.sources), subtitle = stringResource(R.string.sources_more_sub), icon = R.drawable.ms_extension, onClick = { Nav.open(Route.Sources) })
            SettingRow(stringResource(R.string.jobs), subtitle = if (active > 0) stringResource(R.string.jobs_running, active) else stringResource(R.string.jobs_sub), icon = R.drawable.ms_download, onClick = { Nav.open(Route.Jobs) })
            SettingRow(stringResource(R.string.history), icon = R.drawable.ms_history, onClick = { Nav.open(Route.History) })
            SettingRow(stringResource(R.string.settings), subtitle = stringResource(R.string.settings_sub), icon = R.drawable.ms_settings, onClick = { Nav.open(Route.Settings) })
        }
        Column(Modifier.padding(horizontal = 16.dp).glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.app_name), color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(R.string.about_body), color = c.mute, fontSize = 12.sp, lineHeight = 19.sp)
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), color = c.mute, fontSize = 11.sp)
        }
    }
}

@Composable
fun SettingsScreen() {
    val c = LocalRiwaya.current
    val st = Riwaya.settings
    val theme by st.appTheme.flow.collectAsState()
    val order by st.engineOrder.flow.collectAsState()
    val off by st.disabledEngines.flow.collectAsState()
    val key by st.geminiKey.flow.collectAsState()
    val parallel by st.parallelBatches.flow.collectAsState()
    val prefetch by st.prefetchNext.flow.collectAsState()
    val wifi by st.prefetchWifiOnly.flow.collectAsState()
    val auto by st.autoTranslate.flow.collectAsState()
    val titles by st.translateTitles.flow.collectAsState()
    val target by st.targetLanguage.flow.collectAsState()
    val autoNext by st.autoNextChapter.flow.collectAsState()
    val timing by Riwaya.translator.lastTiming.collectAsState()
    val engines = (order.split(',').mapNotNull { runCatching { Engine.valueOf(it.trim()) }.getOrNull() } + Engine.entries).distinct()

    Column(Modifier.fillMaxSize().glowBackground()) {
        TopBar(stringResource(R.string.settings), onBack = { Nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.translation))
            Text(stringResource(R.string.engines_body), color = c.mute, fontSize = 12.sp)
            Column(Modifier.glass()) {
                engines.forEachIndexed { i, e ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = i > 0) { move(engines, i, -1) }, contentAlignment = Alignment.Center) {
                            if (i > 0) Sym(R.drawable.ms_keyboard_arrow_down, tint = c.mute, size = 20.dp, modifier = Modifier.rotate(180f), description = stringResource(R.string.move_up))
                        }
                        Sym(
                            when (e) { Engine.AI -> R.drawable.ms_auto_awesome; Engine.GOOGLE -> R.drawable.ms_translate; Engine.MLKIT -> R.drawable.ms_cloud_off },
                            tint = if (e == Engine.AI) c.amber else c.text,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(when (e) { Engine.AI -> R.string.engine_ai_long; Engine.GOOGLE -> R.string.engine_google; Engine.MLKIT -> R.string.engine_mlkit_long }),
                                color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            )
                            Text(
                                stringResource(when (e) { Engine.AI -> if (key.isBlank()) R.string.engine_ai_no_key else R.string.engine_ai_sub; Engine.GOOGLE -> R.string.engine_google_sub; Engine.MLKIT -> R.string.engine_mlkit_sub }),
                                color = c.mute, fontSize = 11.sp,
                            )
                        }
                        RSwitch(e.name !in off) { on -> st.disabledEngines.set(if (on) off - e.name else off + e.name) }
                    }
                    if (i < engines.size - 1) HorizontalDivider(color = c.glassLine)
                }
            }
            OutlinedTextField(
                value = key, onValueChange = { st.geminiKey.set(it.trim()) }, singleLine = true,
                label = { Text(stringResource(R.string.gemini_key)) },
                supportingText = { Text(stringResource(R.string.gemini_key_help), fontSize = 11.sp) },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.amber, unfocusedBorderColor = c.glassLine, focusedTextColor = c.text, unfocusedTextColor = c.text),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(Modifier.glass()) {
                SettingRow(stringResource(R.string.parallel_batches), subtitle = stringResource(R.string.parallel_batches_sub), icon = R.drawable.ms_bolt) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 2, 3, 4, 6).forEach { n -> Pill("$n", selected = parallel == n) { st.parallelBatches.set(n) } }
                    }
                }
                SettingRow(stringResource(R.string.auto_translate), subtitle = stringResource(R.string.auto_translate_sub), icon = R.drawable.ms_translate) { RSwitch(auto) { st.autoTranslate.set(it) } }
                SettingRow(stringResource(R.string.prefetch), subtitle = stringResource(R.string.prefetch_sub), icon = R.drawable.ms_bolt) { RSwitch(prefetch) { st.prefetchNext.set(it) } }
                SettingRow(stringResource(R.string.wifi_only), icon = R.drawable.ms_language) { RSwitch(wifi) { st.prefetchWifiOnly.set(it) } }
                SettingRow(stringResource(R.string.translate_titles), subtitle = stringResource(R.string.translate_titles_sub), icon = R.drawable.ms_text_fields) { RSwitch(titles) { st.translateTitles.set(it) } }
                SettingRow(stringResource(R.string.target_language), icon = R.drawable.ms_language) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ar" to "العربية", "en" to "English").forEach { (k, l) -> Pill(l, selected = target == k) { st.targetLanguage.set(k) } }
                    }
                }
            }
            TranslationTest()
            timing?.let { (first, all) ->
                Text(stringResource(R.string.last_timing, first / 1000f, all / 1000f), color = c.mute, fontSize = 12.sp)
            }

            SectionHeader(stringResource(R.string.sources))
            Column(Modifier.glass()) {
                val autoHide by st.autoHideBroken.flow.collectAsState()
                SettingRow(stringResource(R.string.auto_hide), subtitle = stringResource(R.string.auto_hide_sub), icon = R.drawable.ms_extension) {
                    RSwitch(autoHide) {
                        st.autoHideBroken.set(it)
                        if (!it) st.autoDisabled.set(emptySet())
                    }
                }
            }

            SectionHeader(stringResource(R.string.listening))
            Column(Modifier.glass()) {
                SettingRow(stringResource(R.string.auto_next), icon = R.drawable.ms_skip_next) { RSwitch(autoNext) { st.autoNextChapter.set(it) } }
            }

            SectionHeader(stringResource(R.string.appearance))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("DARK" to R.string.theme_dark, "LIGHT" to R.string.theme_light, "SYSTEM" to R.string.theme_system).forEach { (k, l) ->
                    Pill(stringResource(l), selected = theme == k, icon = if (k == "DARK") R.drawable.ms_dark_mode else R.drawable.ms_light_mode) { st.appTheme.set(k) }
                }
            }

            SectionHeader(stringResource(R.string.onb_lang_title_short))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val langs by st.languages.flow.collectAsState()
                listOf("ar", "en", "zh", "fr", "ru").forEach { l ->
                    Pill(app.riwaya.engine.SourceCatalog.languageName(l), selected = l in langs) {
                        st.languages.set(if (l in langs) langs - l else langs + l)
                    }
                }
            }
        }
    }
}

private fun move(list: List<Engine>, i: Int, by: Int) {
    val m = list.toMutableList()
    val j = (i + by).coerceIn(0, m.size - 1)
    val e = m.removeAt(i)
    m.add(j, e)
    Riwaya.settings.engineOrder.set(m.joinToString(",") { it.name })
}

@Composable
fun HistoryScreen() {
    val c = LocalRiwaya.current
    val history by Riwaya.library.history.collectAsState()
    val novels by Riwaya.library.novels.collectAsState()
    Column(Modifier.fillMaxSize().glowBackground()) {
        TopBar(stringResource(R.string.history), onBack = { Nav.back() })
        if (history.isEmpty()) {
            EmptyState(R.drawable.ms_history, stringResource(R.string.history_empty))
            return
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history, key = { it.novelId }) { h ->
                val n = novels[h.novelId] ?: return@items
                Row(
                    Modifier.fillMaxWidth().glass().clickable { Nav.open(Route.Reader(n.sourceId, n.key, h.chapterKey)) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(n.cover, n.title, Modifier.size(44.dp, 60.dp), RoundedCornerShape(8.dp), showTitle = false)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(h.chapterName, color = c.mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(DateUtils.getRelativeTimeSpanString(h.at).toString(), color = c.mute, fontSize = 11.sp)
                    }
                    RIconButton(R.drawable.ms_delete, stringResource(R.string.remove), c.mute) { Riwaya.library.removeHistory(h.novelId) }
                }
            }
        }
    }
}

@Composable
fun JobsScreen() {
    val c = LocalRiwaya.current
    val jobs by Jobs.jobs.collectAsState()
    Column(Modifier.fillMaxSize().glowBackground()) {
        TopBar(stringResource(R.string.jobs), onBack = { Nav.back() }, actions = {
            if (jobs.any { it.finished }) RIconButton(R.drawable.ms_done_all, stringResource(R.string.clear_finished)) { Jobs.clearFinished() }
        })
        if (jobs.isEmpty()) {
            EmptyState(R.drawable.ms_download, stringResource(R.string.jobs_empty), stringResource(R.string.jobs_empty_body))
            return
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(jobs, key = { it.id }) { j ->
                Column(Modifier.fillMaxWidth().glass().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Sym(
                            when (j.kind) { JobState.Kind.DOWNLOAD -> R.drawable.ms_download; JobState.Kind.TRANSLATE -> R.drawable.ms_translate; JobState.Kind.PREFETCH -> R.drawable.ms_bolt },
                            tint = c.teal,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(j.novelTitle, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(
                                    when (j.kind) { JobState.Kind.DOWNLOAD -> R.string.job_download; JobState.Kind.TRANSLATE -> R.string.job_translate; JobState.Kind.PREFETCH -> R.string.job_prefetch },
                                    j.done, j.total,
                                ) + if (j.failed > 0) " · " + stringResource(R.string.job_failed, j.failed) else "",
                                color = c.mute, fontSize = 12.sp,
                            )
                        }
                        if (!j.finished) RIconButton(R.drawable.ms_close, stringResource(R.string.cancel), c.mute) { Jobs.cancel(j.id) }
                    }
                    LinearProgressIndicator(progress = { j.done.toFloat() / j.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = if (j.finished) c.ok else c.amber, trackColor = c.glassLine)
                }
            }
        }
    }
}

@Composable
fun GlossaryScreen(route: Route.Glossary) {
    val c = LocalRiwaya.current
    val id = app.riwaya.data.SavedNovel.idOf(route.sourceId, route.key)
    var entries by remember { mutableStateOf(Riwaya.translator.readGlossary(id).toList().sortedBy { it.first }) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    fun save(list: List<Pair<String, String>>) {
        entries = list.sortedBy { it.first }
        Riwaya.translator.writeGlossary(id, list.toMap())
    }
    Column(Modifier.fillMaxSize().glowBackground()) {
        TopBar(stringResource(R.string.glossary), onBack = { Nav.back() }, subtitle = route.title)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.glossary_body), color = c.mute, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.glossary_original)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(to, { to = it }, label = { Text(stringResource(R.string.glossary_translation)) }, singleLine = true, modifier = Modifier.weight(1f))
                RIconButton(R.drawable.ms_check, stringResource(R.string.add), c.amber) {
                    if (from.isNotBlank() && to.isNotBlank()) {
                        save(entries.filter { it.first != from.trim() } + (from.trim() to to.trim()))
                        from = ""
                        to = ""
                    }
                }
            }
            Pill(stringResource(R.string.retranslate_all), icon = R.drawable.ms_refresh) { Riwaya.translator.forgetNovel(id) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (entries.isEmpty()) item { EmptyState(R.drawable.ms_spellcheck, stringResource(R.string.glossary_empty)) }
            items(entries, key = { it.first }) { (k, v) ->
                Row(Modifier.fillMaxWidth().glass().clickable { from = k; to = v }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, color = c.mute, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(v, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    RIconButton(R.drawable.ms_delete, stringResource(R.string.remove), c.mute) { save(entries.filter { it.first != k }) }
                }
            }
        }
    }
}

/** A site opened inside the app, so the reader can pass its check themselves; the cookies are then reused. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(route: Route.Web) {
    val c = LocalRiwaya.current
    var pageTitle by remember { mutableStateOf(route.url) }
    Column(Modifier.fillMaxSize().glowBackground()) {
        TopBar(pageTitle, onBack = { Nav.back() }, subtitle = stringResource(R.string.web_hint))
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = Net.userAgent
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageTitle = view?.title ?: url.orEmpty()
                            CookieManager.getInstance().flush()
                        }
                    }
                    loadUrl(route.url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Translates one sentence with every engine that is on, and shows each answer or its error. */
@Composable
private fun TranslationTest() {
    val c = LocalRiwaya.current
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Pair<Engine, String>>>(emptyList()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sample = stringResource(R.string.test_sample)
    Column(Modifier.fillMaxWidth().glass().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.test_translation), color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Pill(stringResource(if (running) R.string.searching else R.string.test_run), selected = !running, icon = R.drawable.ms_translate) {
                if (!running) {
                    running = true
                    scope.launch {
                        results = Riwaya.translator.test(sample)
                        running = false
                    }
                }
            }
        }
        if (results.isEmpty() && !running) Text(stringResource(R.string.test_hint), color = c.mute, fontSize = 12.sp)
        results.forEach { (e, r) ->
            val bad = r.startsWith("ERROR")
            Text(
                e.name + ": " + r,
                color = if (bad) c.bad else c.text, fontSize = 12.sp,
                style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
            )
        }
    }
}
