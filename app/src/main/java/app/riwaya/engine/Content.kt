package app.riwaya.engine

import com.fleeksoft.ksoup.Ksoup
import ireader.core.source.CatalogSource
import ireader.core.source.HttpSource
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.ImageUrl
import ireader.core.source.model.Page
import ireader.core.source.model.PageUrl
import ireader.core.source.model.Text

/** A chapter's text as paragraphs, from whatever the source sends (plain text or HTML). */
object Content {

    suspend fun fetch(source: CatalogSource, chapter: ChapterInfo): List<String> {
        val pages: List<Page> = source.getPageList(chapter, emptyList())
        val parts = mutableListOf<String>()
        for (page in pages) {
            val complete = if (page is PageUrl && source is HttpSource) runCatching { source.getPage(page) }.getOrNull() else page
            when (complete) {
                is Text -> parts += paragraphs(complete.text)
                is ImageUrl -> Unit
                else -> Unit
            }
        }
        return clean(parts, chapter.name)
    }

    fun paragraphs(text: String): List<String> {
        if (!text.contains('<')) return split(text)
        return runCatching {
            val doc = Ksoup.parse(text)
            doc.select("p, h1, h2, h3, h4, li, blockquote").map { it.text().trim() }.filter { it.isNotEmpty() }
                .ifEmpty { split(doc.text()) }
        }.getOrElse { split(text) }
    }

    private fun split(text: String): List<String> = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Drops a first paragraph that only repeats the chapter's title, and empty lines. */
    fun clean(paragraphs: List<String>, title: String): List<String> {
        fun key(t: String) = t.filter { it.isLetterOrDigit() }.lowercase()
        val list = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        return if (list.isNotEmpty() && key(list.first()).isNotEmpty() && key(list.first()) == key(title)) list.drop(1) else list
    }

    /** Whether most of [text] is Arabic script. */
    fun isArabic(text: String): Boolean {
        var ar = 0
        var latin = 0
        for (c in text.take(800)) {
            if (c in '؀'..'ۿ') ar++ else if (c.isLetter()) latin++
        }
        return ar > latin
    }
}
