package app.riwaya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.data.Levels
import app.riwaya.ui.theme.Fonts
import app.riwaya.ui.theme.LocalRiwaya

/** The reader's level: its name, the chapters read, and how far the next level is. */
@Composable
fun LevelCard(modifier: Modifier = Modifier, detailed: Boolean = false) {
    val c = LocalRiwaya.current
    val novels by Riwaya.library.novels.collectAsState()
    val progress by Riwaya.library.progress.collectAsState()
    val stats = remember(novels, progress) { Levels.stats(novels, progress) }
    Column(modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Sym(stats.level.icon, tint = c.onAccent, size = 24.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.level_n, stats.level.index), color = c.mute, fontSize = 11.sp)
                Text(stringResource(stats.level.name), color = c.text, fontFamily = Fonts.display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stats.chaptersRead.toString(), color = c.amber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.chapters_read), color = c.mute, fontSize = 11.sp)
            }
        }
        LinearProgressIndicator(
            progress = { stats.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = c.amber, trackColor = c.glassLine,
        )
        Text(
            stats.next?.let { stringResource(R.string.to_next_level, stats.toNext, stringResource(it.name)) } ?: stringResource(R.string.max_level),
            color = c.mute, fontSize = 12.sp,
        )
        if (detailed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(stats.novelsStarted.toString(), stringResource(R.string.novels_started), Modifier.weight(1f))
                Stat(stats.novelsFinished.toString(), stringResource(R.string.novels_finished), Modifier.weight(1f))
                Stat(stats.chaptersRead.toString(), stringResource(R.string.chapters_read), Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Levels.all.forEach { l ->
                    val reached = stats.chaptersRead >= l.from
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Sym(l.icon, tint = if (reached) c.amber else c.mute.copy(alpha = 0.5f), size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(l.name), color = if (reached) c.text else c.mute, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.from_chapters, l.from), color = c.mute, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val c = LocalRiwaya.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.glass).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = c.mute, fontSize = 10.5.sp)
    }
}
