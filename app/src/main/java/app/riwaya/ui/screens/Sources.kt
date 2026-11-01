package app.riwaya.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.engine.AvailableExt
import app.riwaya.engine.Extensions
import app.riwaya.engine.NovelExt
import app.riwaya.engine.SourceCatalog
import app.riwaya.engine.SourceKind
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import app.riwaya.ui.components.HealthDot
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.RIconButton
import app.riwaya.ui.components.RSwitch
import app.riwaya.ui.components.Sym
import app.riwaya.ui.components.TopBar
import app.riwaya.ui.components.glass
import app.riwaya.ui.components.glowBackground
import app.riwaya.ui.components.healthText
import app.riwaya.ui.theme.LocalRiwaya
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun SourcesScreen() {
    val c = LocalRiwaya.current
    val st = Riwaya.settings
    val installed by Riwaya.extensions.installed.collectAsState()
    val available by Riwaya.extensions.available.collectAsState()
    val installing by Riwaya.extensions.installing.collectAsState()
    val loading by Riwaya.extensions.loading.collectAsState()
    val checks by Riwaya.health.checks.collectAsState()
    val checking by Riwaya.health.running.collectAsState()
    val disabled by st.disabledSources.flow.collectAsState()
    val pinned by st.pinnedSources.flow.collectAsState()
    val langs by st.languages.flow.collectAsState()
    var kind by remember { mutableStateOf<SourceKind?>(null) }
    var addRepo by remember { mutableStateOf(false) }
    var showAvailable by remember { mutableStateOf(true) }
    val updates = remember(installed, available) { Riwaya.extensions.updates() }

    val have = installed.map { it.pkg }.toSet()
    val groups = installed.filter { kind == null || it.kind == kind }.groupBy { it.lang }
    val order = groups.keys.sortedWith(compareBy({ it !in langs }, { langs.indexOf(it) }, { it }))
    val offered = available.filter { it.pkg !in have && !it.nsfw }.groupBy { it.lang }
    val offeredOrder = offered.keys.sortedWith(compareBy({ it !in langs }, { it }))

    Column(Modifier.fillMaxSize().glowBackground()) {
        TopBar(stringResource(R.string.sources), onBack = { Nav.back() }, subtitle = stringResource(R.string.sources_sub, installed.size, installed.count { it.source.id.toString() !in disabled }), actions = {
            if (checking) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.amber)
                Spacer(Modifier.width(12.dp))
            } else {
                RIconButton(R.drawable.ms_refresh, stringResource(R.string.check_sources)) {
                    Riwaya.scope.launch { Riwaya.health.checkAll(Riwaya.extensions.enabled().map { it.source }) }
                }
            }
            RIconButton(R.drawable.ms_add_link, stringResource(R.string.add_repo)) { addRepo = true }
        })
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Pill(stringResource(R.string.all), selected = kind == null) { kind = null } }
            items(listOf(SourceKind.TRANSLATED to R.string.kind_translated, SourceKind.ORIGINAL to R.string.kind_original, SourceKind.MACHINE to R.string.kind_machine, SourceKind.LIGHT_NOVEL to R.string.kind_light)) { (k, l) ->
                Pill(stringResource(l), selected = kind == k) { kind = k }
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (updates.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().glass().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Sym(R.drawable.ms_update, tint = c.amber)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.updates_available, updates.size), color = c.text, modifier = Modifier.weight(1f))
                        Pill(stringResource(R.string.update_all), selected = true) {
                            Riwaya.scope.launch { updates.values.forEach { Riwaya.extensions.install(it, reloadAfter = false) }; Riwaya.extensions.reload() }
                        }
                    }
                }
            }
            order.forEach { lang ->
                val list = groups[lang].orEmpty().sortedWith(compareBy({ it.source.id.toString() !in pinned }, { it.displayName }))
                item(key = "h$lang") {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (lang in langs) Sym(R.drawable.ms_keep_fill1, tint = c.amber, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(SourceCatalog.languageName(lang), color = c.text, fontWeight = FontWeight.SemiBold)
                        Text(" · ${list.size}", color = c.mute, fontSize = 12.sp)
                    }
                }
                items(list, key = { it.pkg }) { e ->
                    SourceRow(e, checks[e.source.id], e.source.id.toString() !in disabled, e.source.id.toString() in pinned, updates.containsKey(e.pkg))
                }
            }
            if (offered.isNotEmpty()) {
                item(key = "avail") {
                    Row(Modifier.padding(top = 18.dp).clickable { showAvailable = !showAvailable }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.available_sources, offered.values.sumOf { it.size }), color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Sym(R.drawable.ms_keyboard_arrow_down, tint = c.mute)
                    }
                }
                if (showAvailable) {
                    offeredOrder.forEach { lang ->
                        val list = offered[lang].orEmpty()
                        item(key = "ah$lang") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                Text(SourceCatalog.languageName(lang), color = c.mute, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Pill(stringResource(R.string.install_all, list.size), icon = R.drawable.ms_download) {
                                    Riwaya.scope.launch { Riwaya.extensions.installAll(setOf(lang), nsfw = false) }
                                }
                            }
                        }
                        items(list, key = { "a" + it.pkg }) { a -> AvailableRow(a, a.pkg in installing) }
                    }
                }
            } else if (loading) {
                item { app.riwaya.ui.components.Loading() }
            }
            item(key = "repos") {
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.repos), color = c.text, fontWeight = FontWeight.SemiBold)
                    Riwaya.extensions.repos().forEach { r ->
                        Row(Modifier.fillMaxWidth().glass().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.removePrefix("https://"), color = c.mute, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            RIconButton(R.drawable.ms_delete, stringResource(R.string.remove), c.mute) { Riwaya.extensions.removeRepo(r) }
                        }
                    }
                    if (Extensions.IREADER_REPO !in Riwaya.extensions.repos()) {
                        Pill(stringResource(R.string.add_ireader_repo), icon = R.drawable.ms_add_link) { Riwaya.extensions.addRepo(Extensions.IREADER_REPO) }
                    }
                }
            }
        }
    }
    if (addRepo) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addRepo = false },
            title = { Text(stringResource(R.string.add_repo)) },
            text = { OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.repo_url)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (Riwaya.extensions.addRepo(url)) addRepo = false }) { Text(stringResource(R.string.add)) } },
            dismissButton = { TextButton(onClick = { addRepo = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SourceIcon(name: String, url: String) {
    val c = LocalRiwaya.current
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.amber.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Text(name.filter { it.isLetter() }.take(2), color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (url.isNotBlank()) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun SourceRow(e: NovelExt, check: app.riwaya.engine.Health.Check?, enabled: Boolean, pinned: Boolean, hasUpdate: Boolean) {
    val c = LocalRiwaya.current
    val st = Riwaya.settings
    val id = e.source.id.toString()
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().glass().clickable { Nav.open(Route.SourceBrowse(e.source.id)) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceIcon(e.displayName, e.iconUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.displayName, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (e.displayName != e.name) Text("  " + e.name, color = c.mute, fontSize = 11.sp, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HealthDot(check?.status)
                Spacer(Modifier.width(6.dp))
                val ago = check?.at?.takeIf { it > 0 }?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() }
                Text(listOfNotNull(healthText(check), ago).joinToString(" · "), color = c.mute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hasUpdate) Text(stringResource(R.string.update_available), color = c.amber, fontSize = 11.sp)
        }
        Box {
            RIconButton(if (pinned) R.drawable.ms_keep_fill1 else R.drawable.ms_keep, stringResource(R.string.pin), if (pinned) c.amber else c.mute) {
                st.pinnedSources.set(if (pinned) st.pinnedSources.get() - id else st.pinnedSources.get() + id)
            }
        }
        RSwitch(enabled) { on -> st.disabledSources.set(if (on) st.disabledSources.get() - id else st.disabledSources.get() + id) }
        if (!e.builtIn) {
            Box {
                RIconButton(R.drawable.ms_more_vert, stringResource(R.string.more), c.mute) { menu = true }
                androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.check_now)) }, onClick = {
                        menu = false
                        Riwaya.scope.launch { Riwaya.health.checkAll(listOf(e.source)) }
                    })
                    SourceCatalog.meta(e.pkg)?.site?.takeIf { it.isNotBlank() }?.let { site ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.open_in_browser)) }, onClick = { menu = false; Nav.open(Route.Web(site)) })
                    }
                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.uninstall)) }, onClick = {
                        menu = false
                        Riwaya.scope.launch { Riwaya.extensions.uninstall(e.pkg) }
                    })
                }
            }
        }
    }
}

@Composable
private fun AvailableRow(a: AvailableExt, installing: Boolean) {
    val c = LocalRiwaya.current
    Row(Modifier.fillMaxWidth().glass().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SourceIcon(a.displayName, a.iconUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.displayName, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(a.name.takeIf { it != a.displayName }, a.description.ifBlank { null }, "v" + a.versionName).filterNotNull().joinToString(" · "), color = c.mute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (installing) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = c.amber)
        } else {
            RIconButton(R.drawable.ms_download, stringResource(R.string.install), c.amber) { Riwaya.scope.launch { Riwaya.extensions.install(a) } }
        }
    }
}
