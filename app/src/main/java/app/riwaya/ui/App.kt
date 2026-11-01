package app.riwaya.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as SRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.listen.Player
import app.riwaya.ui.components.Cover
import app.riwaya.ui.components.Sym
import app.riwaya.ui.screens.DiscoverScreen
import app.riwaya.ui.screens.GlossaryScreen
import app.riwaya.ui.screens.HistoryScreen
import app.riwaya.ui.screens.HomeScreen
import app.riwaya.ui.screens.JobsScreen
import app.riwaya.ui.screens.LibraryScreen
import app.riwaya.ui.screens.ListenScreen
import app.riwaya.ui.screens.MoreScreen
import app.riwaya.ui.screens.NovelScreen
import app.riwaya.ui.screens.OnboardingScreen
import app.riwaya.ui.screens.ReaderScreen
import app.riwaya.ui.screens.SearchScreen
import app.riwaya.ui.screens.SettingsScreen
import app.riwaya.ui.screens.SourceBrowseScreen
import app.riwaya.ui.screens.SourcesScreen
import app.riwaya.ui.screens.WebScreen
import app.riwaya.ui.theme.LocalRiwaya
import app.riwaya.ui.theme.RiwayaTheme

@Composable
fun App() {
    val theme by Riwaya.settings.appTheme.flow.collectAsState()
    val onboarded by Riwaya.settings.onboarded.flow.collectAsState()
    RiwayaTheme(theme) {
        val c = LocalRiwaya.current
        Box(Modifier.fillMaxSize().background(c.bg)) {
            if (!onboarded) {
                OnboardingScreen()
            } else {
                Main()
            }
        }
    }
}

@Composable
private fun Main() {
    val c = LocalRiwaya.current
    val top = Nav.stack.lastOrNull()
    BackHandler(enabled = top != null || Nav.tab != Tab.HOME) { Nav.back() }
    val immersive = top is Route.Reader || top is Route.ListenFull || top is Route.Web
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = top ?: Nav.tab,
                transitionSpec = { (fadeIn(spring(stiffness = 600f)) + slideInVertically(spring(stiffness = 600f)) { it / 24 }) togetherWith fadeOut(spring(stiffness = 900f)) },
                label = "screen",
            ) { target ->
                when (target) {
                    is Tab -> when (target) {
                        Tab.HOME -> HomeScreen()
                        Tab.DISCOVER -> DiscoverScreen()
                        Tab.LIBRARY -> LibraryScreen()
                        Tab.LISTEN -> ListenScreen(full = false)
                        Tab.MORE -> MoreScreen()
                    }
                    is Route.Novel -> NovelScreen(target)
                    is Route.Reader -> ReaderScreen(target)
                    is Route.Search -> SearchScreen(target)
                    is Route.SourceBrowse -> SourceBrowseScreen(target)
                    is Route.Glossary -> GlossaryScreen(target)
                    is Route.Web -> WebScreen(target)
                    Route.Sources -> SourcesScreen()
                    Route.Settings -> SettingsScreen()
                    Route.History -> HistoryScreen()
                    Route.Jobs -> JobsScreen()
                    Route.ListenFull -> ListenScreen(full = true)
                    else -> Unit
                }
            }
        }
        if (!immersive) {
            MiniPlayer()
            BottomBar()
        }
    }
}

@Composable
private fun BottomBar() {
    val c = LocalRiwaya.current
    val top = Nav.stack.lastOrNull()
    Row(
        Modifier.fillMaxWidth().background(c.bg2).navigationBarsPadding().padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        val items = listOf(
            Triple(Tab.HOME, R.drawable.ms_home, R.string.tab_home),
            Triple(Tab.DISCOVER, R.drawable.ms_explore, R.string.tab_discover),
            Triple(Tab.LIBRARY, R.drawable.ms_auto_stories, R.string.tab_library),
            Triple(Tab.LISTEN, R.drawable.ms_headphones, R.string.tab_listen),
            Triple(Tab.MORE, R.drawable.ms_more_horiz, R.string.tab_more),
        )
        items.forEach { (tab, icon, label) ->
            val on = Nav.tab == tab && top == null
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = SRole.Tab) { Nav.goTab(tab) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) c.amber.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 14.dp, vertical = 3.dp),
                ) {
                    Sym(if (on) filled(icon) else icon, tint = if (on) c.amber else c.mute, size = 22.dp)
                }
                Spacer(Modifier.height(2.dp))
                Text(stringResource(label), fontSize = 10.5.sp, color = if (on) c.amber else c.mute, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

private fun filled(icon: Int): Int = when (icon) {
    R.drawable.ms_home -> R.drawable.ms_home_fill1
    R.drawable.ms_explore -> R.drawable.ms_explore_fill1
    R.drawable.ms_auto_stories -> R.drawable.ms_auto_stories_fill1
    R.drawable.ms_headphones -> R.drawable.ms_headphones_fill1
    else -> icon
}

/** The listening bar above the tabs on every screen while a novel is being read aloud. */
@Composable
private fun MiniPlayer() {
    val c = LocalRiwaya.current
    val s by Player.state.collectAsState()
    AnimatedVisibility(s.active, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val novel = s.novel ?: return@AnimatedVisibility
        Column(
            Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(c.amber.copy(alpha = 0.16f), c.teal.copy(alpha = 0.10f))))
                .clickable { Nav.open(Route.ListenFull) },
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(novel.cover, novel.title, Modifier.size(34.dp, 44.dp), RoundedCornerShape(8.dp), showTitle = false)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(novel.title, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (s.loading) stringResource(R.string.listen_loading) else s.chapter?.name.orEmpty(),
                        color = c.mute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(c.text).clickable { Player.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Sym(if (s.playing) R.drawable.ms_pause_fill1 else R.drawable.ms_play_arrow_fill1, tint = c.bg, size = 22.dp, description = stringResource(if (s.playing) R.string.listen_pause else R.string.listen_play))
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(34.dp).clip(CircleShape).clickable { Player.stop() }, contentAlignment = Alignment.Center) {
                    Sym(R.drawable.ms_close, tint = c.mute, size = 20.dp, description = stringResource(R.string.listen_stop))
                }
            }
            LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = c.amber,
                trackColor = c.glassLine,
            )
        }
    }
}
