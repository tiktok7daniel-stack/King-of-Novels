package app.riwaya.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.engine.SourceCatalog
import app.riwaya.ui.components.AccentButton
import app.riwaya.ui.components.CardShape
import app.riwaya.ui.components.GhostButton
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.Sym
import app.riwaya.ui.components.glass
import app.riwaya.ui.components.glowBackground
import app.riwaya.ui.theme.LocalRiwaya
import kotlinx.coroutines.launch

/** Genres the reader can like, with the words sources use for them. */
object Genres {
    data class Genre(val label: Int, val query: String)

    val all = listOf(
        Genre(R.string.genre_fantasy, "فانتازيا"),
        Genre(R.string.genre_action, "أكشن"),
        Genre(R.string.genre_romance, "رومانسي"),
        Genre(R.string.genre_adventure, "مغامرة"),
        Genre(R.string.genre_cultivation, "زراعة"),
        Genre(R.string.genre_system, "نظام"),
        Genre(R.string.genre_scifi, "خيال علمي"),
        Genre(R.string.genre_mystery, "غموض"),
        Genre(R.string.genre_horror, "رعب"),
        Genre(R.string.genre_comedy, "كوميديا"),
        Genre(R.string.genre_drama, "دراما"),
        Genre(R.string.genre_reincarnation, "تناسخ"),
        Genre(R.string.genre_martial, "فنون قتالية"),
        Genre(R.string.genre_school, "مدرسي"),
        Genre(R.string.genre_historical, "تاريخي"),
        Genre(R.string.genre_slice, "شريحة من الحياة"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen() {
    val c = LocalRiwaya.current
    val st = Riwaya.settings
    var step by remember { mutableIntStateOf(0) }
    var langs by remember { mutableStateOf(st.languages.get()) }
    var genres by remember { mutableStateOf(st.genres.get()) }
    val available by Riwaya.extensions.available.collectAsState()
    val loadingIndex by Riwaya.extensions.loading.collectAsState()
    var installing by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().glowBackground().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp))
                        .then(if (i <= step) Modifier.background(c.accent) else Modifier.background(c.glassLine)),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.onb_step, step + 1, 3), color = c.mute, fontSize = 12.sp)
        AnimatedContent(step, label = "step", modifier = Modifier.weight(1f)) { s ->
            Column(Modifier.fillMaxSize()) {
                when (s) {
                    0 -> {
                        Text(stringResource(R.string.onb_lang_title), style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.padding(top = 6.dp))
                        Text(stringResource(R.string.onb_lang_body), color = c.mute, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        val counts = available.groupingBy { it.lang }.eachCount()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(SourceCatalog.languageNames.keys.toList().filter { it == "ar" || it == "en" || (counts[it] ?: 0) > 0 }) { code ->
                                val on = code in langs
                                Row(
                                    Modifier.fillMaxWidth().glass().then(if (on) Modifier.border(1.dp, c.amber.copy(alpha = 0.6f), CardShape).background(c.amber.copy(alpha = 0.08f)) else Modifier)
                                        .clickable { langs = if (on) langs - code else langs + code }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.bg2), contentAlignment = Alignment.Center) {
                                        Text(if (code == "ar") "ع" else code.uppercase().take(2), color = c.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(SourceCatalog.languageName(code), color = c.text, fontWeight = FontWeight.SemiBold)
                                        val n = counts[code] ?: 0
                                        Text(
                                            if (code == "ar") stringResource(R.string.onb_lang_ar_sub, n + 1) else stringResource(R.string.onb_lang_other_sub, n),
                                            color = c.mute, fontSize = 11.sp,
                                        )
                                    }
                                    Sym(if (on) R.drawable.ms_check_circle_fill1 else R.drawable.ms_radio_button_unchecked, tint = if (on) c.amber else c.mute)
                                }
                            }
                            if (available.isEmpty() && loadingIndex) item { app.riwaya.ui.components.Loading() }
                        }
                    }
                    1 -> {
                        Text(stringResource(R.string.onb_genres_title), style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.padding(top = 6.dp))
                        Text(stringResource(R.string.onb_genres_body), color = c.mute, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Genres.all.forEach { g ->
                                val on = g.query in genres
                                Pill(stringResource(g.label), selected = on) { genres = if (on) genres - g.query else genres + g.query }
                            }
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.onb_sources_title), style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.padding(top = 6.dp))
                        Text(stringResource(R.string.onb_sources_body), color = c.mute, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        val wanted = available.filter { it.lang in langs && !it.nsfw }
                        Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            langs.forEach { l ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Sym(R.drawable.ms_language, tint = c.teal, size = 20.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(SourceCatalog.languageName(l), color = c.text, modifier = Modifier.weight(1f))
                                    Text(stringResource(R.string.onb_count_sources, wanted.count { it.lang == l } + if (l == "ar") 1 else 0), color = c.mute, fontSize = 12.sp)
                                }
                            }
                            if (installing || finished) {
                                LinearProgressIndicator(
                                    progress = { if (total == 0) 0f else done.toFloat() / total },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = c.amber, trackColor = c.glassLine,
                                )
                                Text(
                                    if (finished) stringResource(R.string.onb_installed_done) else stringResource(R.string.onb_installing, done, total),
                                    color = c.mute, fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0 && !installing) GhostButton(stringResource(R.string.back), Modifier.weight(0.4f)) { step-- }
            when (step) {
                0 -> AccentButton(stringResource(R.string.onb_next_genres), Modifier.weight(1f), icon = R.drawable.ms_arrow_forward, enabled = langs.isNotEmpty()) {
                    st.languages.set(langs)
                    step = 1
                }
                1 -> AccentButton(stringResource(R.string.onb_next_sources), Modifier.weight(1f), icon = R.drawable.ms_arrow_forward) {
                    st.genres.set(genres)
                    step = 2
                }
                else -> AccentButton(
                    if (finished) stringResource(R.string.onb_start) else stringResource(R.string.onb_install),
                    Modifier.weight(1f),
                    icon = if (finished) R.drawable.ms_auto_stories_fill1 else R.drawable.ms_download,
                    enabled = !installing,
                ) {
                    if (finished) {
                        st.onboarded.set(true)
                    } else {
                        installing = true
                        scope.launch {
                            Riwaya.extensions.installAll(langs, nsfw = false) { d, t -> done = d; total = t }
                            installing = false
                            finished = true
                            Riwaya.scope.launch { Riwaya.checkSources() }
                        }
                    }
                }
            }
        }
        if (step == 2 && !installing && !finished) {
            Text(
                stringResource(R.string.onb_skip),
                color = c.mute, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).clickable { st.onboarded.set(true) }.padding(8.dp),
            )
        }
    }
}
