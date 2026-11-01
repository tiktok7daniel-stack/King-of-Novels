package app.riwaya.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.Riwaya
import app.riwaya.data.SavedNovel
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import app.riwaya.ui.Tab
import app.riwaya.ui.components.Cover
import app.riwaya.ui.components.EmptyState
import app.riwaya.ui.components.Pill
import app.riwaya.ui.components.RIconButton
import app.riwaya.ui.components.TopBar
import app.riwaya.ui.components.glowBackground
import app.riwaya.ui.theme.LocalRiwaya
import kotlinx.coroutines.launch

private enum class Shelf { READING, ALL, NEW, DONE, TRANSLATED }
private enum class Sort { RECENT, ADDED, TITLE, UNREAD }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen() {
    val c = LocalRiwaya.current
    val novels by Riwaya.library.novels.collectAsState()
    val progress by Riwaya.library.progress.collectAsState()
    var shelf by remember { mutableStateOf(Shelf.ALL) }
    var sort by remember { mutableStateOf(Sort.RECENT) }
    var sortMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val all = novels.values.filter { it.inLibrary }

    fun readCount(n: SavedNovel) = progress[n.id]?.count { it.value.read } ?: 0
    val list = all.filter { n ->
        when (shelf) {
            Shelf.ALL -> true
            Shelf.READING -> n.lastChapterKey.isNotEmpty() && readCount(n) < n.totalChapters
            Shelf.NEW -> n.newChapters > 0
            Shelf.DONE -> n.totalChapters > 0 && readCount(n) >= n.totalChapters
            Shelf.TRANSLATED -> Riwaya.translator.cachedKeys(n.id).isNotEmpty()
        }
    }.let { l ->
        when (sort) {
            Sort.RECENT -> l.sortedByDescending { maxOf(it.lastReadAt, it.addedAt) }
            Sort.ADDED -> l.sortedByDescending { it.addedAt }
            Sort.TITLE -> l.sortedBy { it.title }
            Sort.UNREAD -> l.sortedByDescending { it.totalChapters - readCount(it) }
        }
    }

    Column(Modifier.fillMaxSize().glowBackground()) {
        if (selected.isEmpty()) {
            TopBar(stringResource(R.string.tab_library), onBack = null, subtitle = stringResource(R.string.library_count, all.size), actions = {
                Box {
                    RIconButton(R.drawable.ms_tune, stringResource(R.string.sort)) { sortMenu = true }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        listOf(Sort.RECENT to R.string.sort_recent, Sort.ADDED to R.string.sort_added, Sort.TITLE to R.string.sort_title, Sort.UNREAD to R.string.sort_unread).forEach { (s, l) ->
                            DropdownMenuItem(text = { Text(stringResource(l), fontWeight = if (sort == s) FontWeight.Bold else FontWeight.Normal) }, onClick = { sort = s; sortMenu = false })
                        }
                    }
                }
                RIconButton(R.drawable.ms_search, stringResource(R.string.search)) { Nav.open(Route.Search()) }
            })
        } else {
            TopBar(stringResource(R.string.selected_count, selected.size), onBack = { selected = emptySet() }, actions = {
                RIconButton(R.drawable.ms_done_all, stringResource(R.string.mark_read)) {
                    selected.forEach { id ->
                        val n = novels[id] ?: return@forEach
                        Riwaya.scope.launch { Riwaya.library.cachedChapters(id)?.let { chs -> Riwaya.library.markRead(id, chs.map { it.key }, true) } }
                        Riwaya.library.clearNew(n.sourceId, n.key)
                    }
                    selected = emptySet()
                }
                RIconButton(R.drawable.ms_delete, stringResource(R.string.remove_library)) {
                    selected.forEach { id -> novels[id]?.let { Riwaya.library.setInLibrary(it.sourceId, it.key, false) } }
                    selected = emptySet()
                }
            })
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(Shelf.ALL to R.string.shelf_all, Shelf.READING to R.string.shelf_reading, Shelf.NEW to R.string.shelf_new, Shelf.DONE to R.string.shelf_done, Shelf.TRANSLATED to R.string.shelf_translated)) { (s, l) ->
                Pill(stringResource(l), selected = shelf == s) { shelf = s }
            }
        }
        if (list.isEmpty()) {
            EmptyState(R.drawable.ms_auto_stories, stringResource(R.string.library_empty), stringResource(R.string.library_empty_body), action = stringResource(R.string.tab_discover)) { Nav.goTab(Tab.DISCOVER) }
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(list, key = { it.id }) { n ->
                    val sel = n.id in selected
                    val read = readCount(n)
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(
                            onClick = {
                                if (selected.isNotEmpty()) selected = if (sel) selected - n.id else selected + n.id
                                else Nav.open(Route.Novel(n.sourceId, n.key, n.title, n.cover))
                            },
                            onLongClick = { selected = if (sel) selected - n.id else selected + n.id },
                        ),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box {
                            Cover(n.cover, n.title, Modifier.fillMaxWidth().aspectRatio(0.71f).then(if (sel) Modifier.border(3.dp, c.amber, RoundedCornerShape(12.dp)) else Modifier))
                            if (n.newChapters > 0) {
                                Text(
                                    "+${n.newChapters}",
                                    color = androidx.compose.ui.graphics.Color(0xFF032521), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(10.dp)).background(c.teal).padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(n.title, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                        Text(
                            if (n.totalChapters > 0 && read >= n.totalChapters) stringResource(R.string.shelf_done) else "$read / ${n.totalChapters}",
                            color = c.mute, fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
