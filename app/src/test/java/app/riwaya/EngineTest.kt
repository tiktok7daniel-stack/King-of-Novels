package app.riwaya

import app.riwaya.engine.Search
import app.riwaya.engine.SourceCatalog
import app.riwaya.listen.Role
import app.riwaya.listen.Speaker
import app.riwaya.translate.Translator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    @Test
    fun batchesKeepEveryParagraphInOrder() {
        val paragraphs = List(40) { "p$it ".repeat(30 + it) }
        val batches = Translator.batches(paragraphs.indices.toList(), paragraphs, 1_800)
        assertEquals(paragraphs.indices.toList(), batches.flatten())
        assertTrue(batches.all { b -> b.size == 1 || b.sumOf { paragraphs[it].length } <= 1_800 })
    }

    @Test
    fun sameNovelMatchesAcrossSpellings() {
        assertEquals(Search.normalize("سيّد الغوامض"), Search.normalize("سيد الغوامض"))
        assertEquals(Search.normalize("أسطورة"), Search.normalize("اسطوره"))
        assertEquals(Search.normalize("Lord of the Mysteries!"), Search.normalize("lord of the mysteries"))
    }

    @Test
    fun arabicSourcesHaveArabicNames() {
        assertEquals("ملوك الروايات", SourceCatalog.displayName("ireader.kolnovel.ar", "ar", "KolNovel"))
        assertEquals("RoyalRoad", SourceCatalog.displayName("ireader.royalroad.en", "en", "RoyalRoad"))
    }

    @Test
    fun dialogueGoesToCharacterVoice() {
        val lines = Speaker.lines("قالت ليلى «لن أعود» ثم مشت", multiVoice = true)
        assertTrue(lines.any { it.role == Role.FEMALE && it.text == "لن أعود" })
        assertTrue(lines.first().role == Role.NARRATOR)
    }
}

class MarkReadTest {
    private val chapters = List(50) { i -> ireader.core.source.model.ChapterInfo(key = "k$i", name = "الفصل ${i + 1}: عنوان", number = (i + 1).toFloat()) }

    @org.junit.Test
    fun chapterByNumber() {
        org.junit.Assert.assertEquals(11, app.riwaya.ui.screens.findChapter(chapters, "12"))
        org.junit.Assert.assertEquals(11, app.riwaya.ui.screens.findChapter(chapters, "١٢"))
        org.junit.Assert.assertEquals(null, app.riwaya.ui.screens.findChapter(chapters, "99"))
    }

    @org.junit.Test
    fun levelsGrowWithChapters() {
        org.junit.Assert.assertEquals(1, app.riwaya.data.Levels.levelFor(0).index)
        org.junit.Assert.assertEquals(3, app.riwaya.data.Levels.levelFor(150).index)
        org.junit.Assert.assertEquals(9, app.riwaya.data.Levels.levelFor(20_000).index)
    }
}
