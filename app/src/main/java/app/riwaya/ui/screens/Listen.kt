package app.riwaya.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.listen.Player
import app.riwaya.listen.Role
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import app.riwaya.ui.components.Cover
import app.riwaya.ui.components.EmptyState
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.RIconButton
import app.riwaya.ui.components.RSwitch
import app.riwaya.ui.components.SectionHeader
import app.riwaya.ui.components.SheetShape
import app.riwaya.ui.components.Sym
import app.riwaya.ui.components.TopBar
import app.riwaya.ui.components.glass
import app.riwaya.ui.components.glowBackground
import app.riwaya.ui.theme.Fonts
import app.riwaya.ui.theme.LocalRiwaya
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(full: Boolean) {
    val c = LocalRiwaya.current
    val s by Player.state.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }

    if (!s.active) {
        Column(Modifier.fillMaxSize().glowBackground()) {
            TopBar(stringResource(R.string.tab_listen), onBack = if (full) { { Nav.back(); Unit } } else null)
            val novels by Riwaya.library.novels.collectAsState()
            val recent = novels.values.filter { it.lastChapterKey.isNotEmpty() }.sortedByDescending { maxOf(it.lastListened, it.lastReadAt) }.take(12)
            if (recent.isEmpty()) {
                EmptyState(R.drawable.ms_headphones, stringResource(R.string.listen_empty), stringResource(R.string.listen_empty_body))
            } else {
                SectionHeader(stringResource(R.string.listen_continue), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    items(recent, key = { it.id }) { n ->
                        Row(
                            Modifier.fillMaxWidth().glass().clickable {
                                Riwaya.scope.launch {
                                    val chapters = Riwaya.library.cachedChapters(n.id).orEmpty()
                                    val i = chapters.indexOfFirst { it.key == n.lastChapterKey }.coerceAtLeast(0)
                                    if (chapters.isNotEmpty()) {
                                        val para = Riwaya.library.chapterState(n.id, chapters[i].key)?.paragraph ?: 0
                                        Player.start(n, chapters, i, para)
                                    }
                                }
                            }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(n.cover, n.title, Modifier.size(48.dp, 66.dp), RoundedCornerShape(9.dp), showTitle = false)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(n.lastChapterName, color = c.mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                                Sym(R.drawable.ms_play_arrow_fill1, tint = c.onAccent)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val novel = s.novel!!
    val transition = rememberInfiniteTransition(label = "wave")
    val pulse by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")

    Column(Modifier.fillMaxSize().glowBackground().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (full) RIconButton(R.drawable.ms_keyboard_arrow_down, stringResource(R.string.back)) { Nav.back() } else Spacer(Modifier.width(48.dp))
            Text(stringResource(R.string.listen_now), color = c.mute, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            RIconButton(R.drawable.ms_menu_book, stringResource(R.string.read)) {
                s.chapter?.let { Nav.open(Route.Reader(novel.sourceId, novel.key, it.key)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Cover(
            novel.cover, novel.title,
            Modifier.size(180.dp, 244.dp).shadow(30.dp, RoundedCornerShape(22.dp), ambientColor = c.amber, spotColor = c.amber),
            RoundedCornerShape(22.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(s.chapter?.name.orEmpty(), color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (s.loading) stringResource(R.string.listen_loading) else stringResource(R.string.listen_paragraph, s.paragraph + 1, s.paragraphs.size) +
                if (s.translated) " · " + stringResource(R.string.translated) else "",
            color = c.mute, fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Text(
                s.paragraphs.getOrNull(s.paragraph).orEmpty().ifEmpty { s.error.orEmpty() },
                fontFamily = Fonts.naskh, fontSize = 17.sp, lineHeight = 31.sp, color = c.amber, textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Content),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Row(Modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(6, 14, 22, 10, 18, 26, 12, 20, 8, 16, 24, 10, 6, 18, 12).forEachIndexed { i, h ->
                val f = if (s.playing) (if (i % 2 == 0) pulse else 1.4f - pulse) else 0.3f
                Box(Modifier.width(3.dp).height((h * f).dp.coerceAtLeast(3.dp)).clip(RoundedCornerShape(2.dp)).background(c.teal.copy(alpha = 0.8f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = c.amber, trackColor = c.glassLine)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RIconButton(R.drawable.ms_skip_next, stringResource(R.string.next_chapter)) { Player.nextChapter() }
            RIconButton(R.drawable.ms_forward_10, stringResource(R.string.listen_next_paragraph)) { Player.nextParagraph() }
            Box(
                Modifier.size(68.dp).shadow(16.dp, CircleShape, ambientColor = c.coral, spotColor = c.coral).clip(CircleShape).background(c.accent).clickable { Player.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Sym(if (s.playing) R.drawable.ms_pause_fill1 else R.drawable.ms_play_arrow_fill1, tint = c.onAccent, size = 36.dp, description = stringResource(if (s.playing) R.string.listen_pause else R.string.listen_play))
            }
            RIconButton(R.drawable.ms_replay_10, stringResource(R.string.listen_prev_paragraph)) { Player.previousParagraph() }
            RIconButton(R.drawable.ms_skip_previous, stringResource(R.string.prev_chapter)) { Player.previousChapter() }
        }
        Spacer(Modifier.height(14.dp))
        val rate by Riwaya.settings.ttsRate.flow.collectAsState()
        val multi by Riwaya.settings.multiVoice.flow.collectAsState()
        Row(Modifier.fillMaxWidth().glass().padding(vertical = 6.dp)) {
            Option(R.drawable.ms_speed, "%.2f×".format(rate), stringResource(R.string.speed), Modifier.weight(1f)) { sheet = "speed" }
            Option(R.drawable.ms_record_voice_over, stringResource(if (multi) R.string.voices_multi else R.string.voices_one), stringResource(R.string.voices), Modifier.weight(1f)) { sheet = "voices" }
            Option(
                R.drawable.ms_bedtime,
                when {
                    s.sleepEndOfChapter -> stringResource(R.string.sleep_chapter_short)
                    s.sleepEndsAt > 0 -> stringResource(R.string.minutes_short, ((s.sleepEndsAt - System.currentTimeMillis()) / 60000 + 1).toInt())
                    else -> stringResource(R.string.off)
                },
                stringResource(R.string.sleep_timer), Modifier.weight(1f),
            ) { sheet = "sleep" }
            Option(R.drawable.ms_translate, stringResource(if (s.translated) R.string.translated else R.string.original), stringResource(R.string.text), Modifier.weight(1f)) {}
        }
        Spacer(Modifier.height(12.dp))
    }

    sheet?.let { which ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, shape = SheetShape, containerColor = c.bg2) {
            when (which) {
                "speed" -> SpeedSheet()
                "voices" -> VoicesSheet(s.language)
                else -> SleepSheet { sheet = null }
            }
        }
    }
}

@Composable
private fun Option(icon: Int, value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalRiwaya.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Sym(icon, tint = c.mute, size = 20.dp)
        Text(value, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = c.mute, fontSize = 10.5.sp, maxLines = 1)
    }
}

@Composable
private fun SpeedSheet() {
    val c = LocalRiwaya.current
    val st = Riwaya.settings
    val rate by st.ttsRate.flow.collectAsState()
    val pitch by st.ttsPitch.flow.collectAsState()
    val pause by st.pauseMs.flow.collectAsState()
    val colors = SliderDefaults.colors(thumbColor = c.amber, activeTrackColor = c.amber, inactiveTrackColor = c.glassLine)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.speed_value, "%.2f".format(rate)), color = c.text)
        Slider(rate, { st.ttsRate.set((it * 20).toInt() / 20f) }, valueRange = 0.5f..3f, onValueChangeFinished = { Player.refresh() }, colors = colors)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.8f, 1f, 1.25f, 1.5f, 2f).forEach { v -> Pill("${v}×", selected = rate == v) { st.ttsRate.set(v); Player.refresh() } }
        }
        Text(stringResource(R.string.pitch_value, "%.2f".format(pitch)), color = c.text)
        Slider(pitch, { st.ttsPitch.set((it * 20).toInt() / 20f) }, valueRange = 0.5f..2f, onValueChangeFinished = { Player.refresh() }, colors = colors)
        Text(stringResource(R.string.pause_value, pause), color = c.text)
        Slider(pause.toFloat(), { st.pauseMs.set(it.toInt()) }, valueRange = 0f..1500f, colors = colors)
    }
}

@Composable
private fun VoicesSheet(language: String) {
    val c = LocalRiwaya.current
    val st = Riwaya.settings
    val multi by st.multiVoice.flow.collectAsState()
    val voices = remember(language) { Player.voices(language) }
    val narrator by st.voiceNarrator.flow.collectAsState()
    val male by st.voiceMale.flow.collectAsState()
    val female by st.voiceFemale.flow.collectAsState()
    var role by remember { mutableStateOf(Role.NARRATOR) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.multi_voice), color = c.text, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.multi_voice_body), color = c.mute, fontSize = 12.sp)
            }
            RSwitch(multi) { st.multiVoice.set(it); Player.refresh() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(stringResource(R.string.role_narrator), selected = role == Role.NARRATOR) { role = Role.NARRATOR }
            if (multi) {
                Pill(stringResource(R.string.role_male), selected = role == Role.MALE) { role = Role.MALE }
                Pill(stringResource(R.string.role_female), selected = role == Role.FEMALE) { role = Role.FEMALE }
            }
        }
        val current = when (role) { Role.NARRATOR -> narrator; Role.MALE -> male; Role.FEMALE -> female }
        if (voices.isEmpty()) {
            Text(stringResource(R.string.no_voices), color = c.mute, fontSize = 13.sp)
        }
        LazyColumn(Modifier.height(280.dp)) {
            item {
                VoiceRow(stringResource(R.string.voice_auto), current.isEmpty()) { setVoice(role, "") }
            }
            items(voices, key = { it.id }) { v ->
                VoiceRow(v.label + if (!v.offline) " · " + stringResource(R.string.online) else "", current == v.id) { setVoice(role, v.id) }
            }
        }
    }
}

private fun setVoice(role: Role, id: String) {
    val st = Riwaya.settings
    when (role) {
        Role.NARRATOR -> st.voiceNarrator.set(id)
        Role.MALE -> st.voiceMale.set(id)
        Role.FEMALE -> st.voiceFemale.set(id)
    }
    Player.refresh()
}

@Composable
private fun VoiceRow(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalRiwaya.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Sym(if (on) R.drawable.ms_check_circle_fill1 else R.drawable.ms_radio_button_unchecked, tint = if (on) c.amber else c.mute, size = 20.dp)
    }
}

@Composable
private fun SleepSheet(close: () -> Unit) {
    val c = LocalRiwaya.current
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.sleep_timer), color = c.text, fontWeight = FontWeight.SemiBold)
        listOf(-1 to stringResource(R.string.sleep_end_chapter), 15 to stringResource(R.string.minutes, 15), 30 to stringResource(R.string.minutes, 30), 60 to stringResource(R.string.minutes, 60), 0 to stringResource(R.string.off)).forEach { (m, label) ->
            Text(label, color = c.text, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Player.sleep(m); close() }.padding(14.dp))
        }
    }
}
