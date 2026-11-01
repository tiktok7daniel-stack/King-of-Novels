package app.riwaya.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.riwaya.R

/** The reader's level, earned by the chapters they read (in all novels). */
object Levels {

    data class Level(val index: Int, val from: Int, @StringRes val name: Int, @DrawableRes val icon: Int)

    val all = listOf(
        Level(1, 0, R.string.level_1, R.drawable.ms_menu_book),
        Level(2, 25, R.string.level_2, R.drawable.ms_auto_stories),
        Level(3, 100, R.string.level_3, R.drawable.ms_bookmark_fill1),
        Level(4, 300, R.string.level_4, R.drawable.ms_explore_fill1),
        Level(5, 700, R.string.level_5, R.drawable.ms_local_fire_department_fill1),
        Level(6, 1_500, R.string.level_6, R.drawable.ms_auto_awesome_fill1),
        Level(7, 3_000, R.string.level_7, R.drawable.ms_new_releases_fill1),
        Level(8, 6_000, R.string.level_8, R.drawable.ms_keep_fill1),
        Level(9, 10_000, R.string.level_9, R.drawable.ms_favorite_fill1),
    )

    data class Stats(
        val chaptersRead: Int,
        val novelsStarted: Int,
        val novelsFinished: Int,
        val level: Level,
        val next: Level?,
    ) {
        /** How far to the next level, 0 to 1. */
        val progress: Float get() = next?.let { ((chaptersRead - level.from).toFloat() / (it.from - level.from)).coerceIn(0f, 1f) } ?: 1f
        val toNext: Int get() = next?.let { it.from - chaptersRead } ?: 0
    }

    fun levelFor(chapters: Int): Level = all.last { chapters >= it.from }

    fun stats(novels: Map<String, SavedNovel>, progress: Map<String, Map<String, ChapterState>>): Stats {
        val readByNovel = progress.mapValues { (_, m) -> m.count { it.value.read } }
        val total = readByNovel.values.sum()
        val finished = novels.values.count { n -> n.totalChapters > 0 && (readByNovel[n.id] ?: 0) >= n.totalChapters }
        val started = readByNovel.count { it.value > 0 }
        val level = levelFor(total)
        return Stats(total, started, finished, level, all.getOrNull(all.indexOf(level) + 1))
    }
}
