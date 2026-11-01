package app.riwaya.engine.builtin

import app.riwaya.core.Net
import app.riwaya.core.text
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import ireader.core.source.CatalogSource
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import ireader.core.source.model.Command
import java.io.IOException
import java.net.URLEncoder

/**
 * ملوك الروايات المجاني (free.kolnovel.com), a LightNovel WordPress site, read the way LNReader's plugin reads it:
 * the series list, the novel page, and the chapter's paragraphs (without the hidden ones the site adds as noise).
 */
class FreeKolNovel : CatalogSource {

    override val id: Long = 7_311_000_000_000_001L
    override val name: String = "Free Kol Novel"
    override val lang: String = "ar"

    private val site = "https://free.kolnovel.com"

    object Popular : Listing("Popular")
    object Latest : Listing("Latest")

    override fun getListings(): List<Listing> = listOf(Popular, Latest)
    override fun getFilters(): FilterList = listOf(Filter.Title())
    override fun getCommands(): CommandList = emptyList()

    private suspend fun fetch(url: String): Document {
        val html = Net.client.text(Net.get(url, mapOf("Referer" to "$site/")))
        val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.trim()
        if (title in CHALLENGE_TITLES) throw IOException("CHALLENGE")
        return Ksoup.parse(html, url)
    }

    private fun abs(href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> site + href
        else -> "$site/$href"
    }

    private fun novels(doc: Document): MangasPageInfo {
        val list = doc.select("article").mapNotNull { a ->
            val link = a.selectFirst("a[href][title]") ?: return@mapNotNull null
            val img = a.selectFirst("img")
            val cover = img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src").orEmpty()
            MangaInfo(key = abs(link.attr("href")), title = link.attr("title").trim(), cover = cover)
        }
        val next = doc.selectFirst("a.next, a.r, .hpage a.r") != null
        return MangasPageInfo(list, next || list.size >= 10)
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val order = if (sort == Latest) "update" else "popular"
        return novels(fetch("$site/series/?page=$page&order=$order"))
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.filterIsInstance<Filter.Text>().firstOrNull { it.value.isNotBlank() }?.value.orEmpty()
        if (query.isBlank()) return getMangaList(Popular, page)
        val q = URLEncoder.encode(query, "UTF-8")
        return novels(fetch("$site/page/$page/?s=$q"))
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val doc = fetch(manga.key)
        val img = doc.selectFirst(".ts-post-image")
        val title = img?.attr("title")?.ifBlank { null } ?: doc.selectFirst("h1.entry-title")?.text() ?: manga.title
        val cover = img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src") ?: manga.cover
        val genres = doc.select(".genxed a, .sertogenre a").map { it.text().trim() }.filter { it.isNotEmpty() }
        val summary = doc.select(".entry-content p, [itemprop=description] p").joinToString("\n\n") { it.text().trim() }
            .ifBlank { doc.selectFirst(".entry-content, [itemprop=description]")?.text().orEmpty() }
        var author = ""
        var status = MangaInfo.UNKNOWN
        doc.select(".spe span, .serl span, .sertostat span").forEach { s ->
            val t = s.text()
            when {
                t.contains("الكاتب") || t.contains("Author", true) -> author = t.substringAfter(':').trim()
                t.contains("الحالة") || t.contains("Status", true) -> status = when {
                    t.contains("مكتملة") || t.contains("Completed", true) -> MangaInfo.COMPLETED
                    t.contains("مستمرة") || t.contains("Ongoing", true) -> MangaInfo.ONGOING
                    t.contains("متوقفة") || t.contains("Hiatus", true) -> MangaInfo.ON_HIATUS
                    else -> MangaInfo.UNKNOWN
                }
            }
        }
        return manga.copy(title = title, cover = cover, genres = genres, description = summary, author = author, status = status)
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val doc = fetch(manga.key)
        return doc.select(".eplister li").mapNotNull { li ->
            val a = li.selectFirst("a[href]") ?: return@mapNotNull null
            val num = li.selectFirst(".epl-num")?.text().orEmpty()
            val title = li.selectFirst(".epl-title")?.text().orEmpty()
            val locked = li.selectFirst(".epl-price")?.text()?.trim()?.let { it.isNotEmpty() && it != "مجاني" && !it.equals("free", true) } == true
            if (locked) return@mapNotNull null
            val name = listOf(num, title).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { a.text() }
            ChapterInfo(key = abs(a.attr("href")), name = name).withAutoNumber().let {
                if (it.number < 0) it.copy(number = Regex("(\\d+)").findAll(num).lastOrNull()?.value?.toFloatOrNull() ?: -1f) else it
            }
        }.reversed()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val doc = fetch(chapter.key)
        // The site adds paragraphs hidden by a class in a <style> to spoil copies; they are not part of the chapter
        val hidden = Regex("\\.(\\w+)(?=\\s*[,{])").findAll(doc.select("article > style").html())
            .map { it.groupValues[1] }.toSet()
        val content = doc.selectFirst(".epcontent") ?: return emptyList()
        content.select("script, style, noscript, .code-block").remove()
        val paragraphs = content.select("p").filter { p -> hidden.none { p.hasClass(it) } }
            .map { it.text().trim() }.filter { it.isNotEmpty() }
        return listOf(Text(paragraphs.joinToString("\n")))
    }

    private companion object {
        val CHALLENGE_TITLES = setOf("Bot Verification", "You are being redirected...", "Un instant...", "Just a moment...", "Redirecting...")
    }
}
