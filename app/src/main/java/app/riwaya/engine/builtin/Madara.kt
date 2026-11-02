package app.riwaya.engine.builtin

import app.riwaya.core.Net
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import ireader.core.source.CatalogSource
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/**
 * A novel site built on the Madara WordPress theme, read the way LNReader's Madara plugin reads it: the search page
 * for lists, the novel page, its chapters from `ajax/chapters/` (or the older admin-ajax call), and the chapter text.
 */
open class Madara(
    override val id: Long,
    override val name: String,
    override val lang: String,
    site: String,
) : CatalogSource {

    protected val site = site.trimEnd('/')

    object Popular : Listing("Popular")
    object Latest : Listing("Latest")
    object Trending : Listing("Trending")

    override fun getListings(): List<Listing> = listOf(Popular, Latest, Trending)
    override fun getFilters(): FilterList = listOf(Filter.Title())
    override fun getCommands(): CommandList = emptyList()

    private fun headers(referer: String = "$site/") = mapOf("Referer" to referer, "Accept-Language" to "en-US,en;q=0.9")

    protected suspend fun html(url: String): String = withContext(Dispatchers.IO) {
        Net.client.newCall(Net.get(url, headers())).execute().use { r ->
            if (r.code == 403 || r.code == 503) throw IOException("CHALLENGE ${r.code}")
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
    }

    protected suspend fun fetch(url: String): Document {
        val text = html(url)
        val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)?.trim()
        if (title in CHALLENGE_TITLES) throw IOException("CHALLENGE")
        return Ksoup.parse(text, url)
    }

    private suspend fun post(url: String, referer: String, body: okhttp3.RequestBody): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).post(body).apply { headers(referer).forEach { (k, v) -> header(k, v) } }
            .header("X-Requested-With", "XMLHttpRequest").build()
        Net.client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body.string() else "" }
    }

    protected fun abs(href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> site + href
        else -> "$site/$href"
    }

    private fun cover(img: Element?): String {
        img ?: return ""
        val candidates = listOf(
            img.attr("data-lazy-src"), img.attr("data-src"), img.attr("data-cfsrc"),
            img.attr("data-srcset").substringBefore(' '), img.attr("data-lazy-srcset").substringBefore(' '),
            img.attr("srcset").substringBefore(' '), img.attr("src"),
        )
        return candidates.firstOrNull { it.isNotBlank() && !it.contains("dflazy") && !it.startsWith("data:") }?.let { abs(it) }.orEmpty()
    }

    private fun novels(doc: Document): MangasPageInfo {
        doc.select(".manga-title-badges").remove()
        val list = doc.select(".page-item-detail, .c-tabs-item__content").mapNotNull { item ->
            val title = item.selectFirst(".post-title") ?: return@mapNotNull null
            val href = title.selectFirst("a")?.attr("href").orEmpty()
            val name = title.text().trim()
            if (name.isEmpty() || href.isEmpty()) return@mapNotNull null
            MangaInfo(key = abs(href), title = name, cover = cover(item.selectFirst("img")))
        }
        val next = doc.selectFirst(".nav-previous a, a.nextpostslink, .wp-pagenavi a.next, a.next") != null
        return MangasPageInfo(list, next || list.size >= 10)
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val order = when (sort) {
            Latest -> "latest"
            Trending -> "trending"
            else -> "views"
        }
        return novels(fetch("$site/page/$page/?s=&post_type=wp-manga&m_orderby=$order"))
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.filterIsInstance<Filter.Text>().firstOrNull { it.value.isNotBlank() }?.value.orEmpty()
        if (query.isBlank()) return getMangaList(Popular, page)
        return novels(fetch("$site/page/$page/?s=${URLEncoder.encode(query, "UTF-8")}&post_type=wp-manga"))
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val doc = fetch(manga.key)
        doc.select(".manga-title-badges, #manga-title span").remove()
        val title = doc.selectFirst(".post-title h1, #manga-title h1, .manga-title")?.text()?.trim().orEmpty().ifBlank { manga.title }
        var author = ""
        var genres = emptyList<String>()
        var status = MangaInfo.UNKNOWN
        doc.select(".post-content_item, .post-content").forEach { item ->
            val head = item.selectFirst("h5")?.text()?.trim().orEmpty()
            val value = item.selectFirst(".summary-content") ?: return@forEach
            when {
                head.startsWith("Genre") || head.startsWith("Tag") || head == "التصنيفات" -> genres = genres + value.select("a").map { it.text().trim() }
                head.startsWith("Author") || head.startsWith("المؤلف") -> author = value.text().trim()
                head.startsWith("Status") || head == "Novel" || head == "الحالة" -> status = statusOf(value.text())
            }
        }
        if (genres.isEmpty()) genres = doc.select(".genres-content a").map { it.text().trim() }
        if (author.isBlank()) author = doc.selectFirst(".author-content a, .manga-author a")?.text()?.trim().orEmpty()
        if (status == MangaInfo.UNKNOWN) doc.selectFirst(".manga-status, .post-status .summary-content")?.text()?.let { status = statusOf(it) }
        doc.select("div.summary__content .code-block, script, noscript").remove()
        val summary = doc.selectFirst("div.summary__content")?.let { s ->
            s.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }.joinToString("\n\n").ifBlank { s.text().trim() }
        } ?: doc.selectFirst("#tab-manga-about, .manga-excerpt, .manga-summary")?.text()?.trim().orEmpty()
        return manga.copy(
            title = title,
            cover = cover(doc.selectFirst(".summary_image img")).ifBlank { manga.cover },
            author = author,
            genres = genres.filter { it.isNotBlank() }.distinct(),
            description = summary,
            status = status,
        )
    }

    private fun statusOf(text: String): Long {
        val t = text.lowercase()
        return when {
            "ongoing" in t || "on-going" in t || "مستمرة" in t -> MangaInfo.ONGOING
            "completed" in t || "end" in t || "مكتملة" in t -> MangaInfo.COMPLETED
            "hold" in t || "hiatus" in t -> MangaInfo.ON_HIATUS
            "cancel" in t -> MangaInfo.CANCELLED
            else -> MangaInfo.UNKNOWN
        }
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val novelUrl = manga.key.trimEnd('/') + "/"
        // The newer endpoint: the chapters of the novel page, in pages when there are many
        var doc = Ksoup.parse(post("${novelUrl}ajax/chapters/", novelUrl, ByteArray(0).toRequestBody(null)), novelUrl)
        val pages = doc.select("[data-page]").mapNotNull { it.attr("data-page").toIntOrNull() }.maxOrNull() ?: 1
        if (pages > 1) {
            val more = (2..pages).map { p -> post("${novelUrl}ajax/chapters/?t=$p", novelUrl, ByteArray(0).toRequestBody(null)) }
            doc = Ksoup.parse(doc.html() + more.joinToString(""), novelUrl)
        }
        var items = doc.select(".wp-manga-chapter")
        if (items.isEmpty()) {
            // The older call, with the novel's id from its page
            val page = fetch(novelUrl)
            items = page.select(".wp-manga-chapter")
            if (items.isEmpty()) {
                val mangaId = page.selectFirst(".rating-post-id")?.attr("value")?.ifBlank { null }
                    ?: page.selectFirst("#manga-chapters-holder")?.attr("data-id").orEmpty()
                if (mangaId.isNotBlank()) {
                    val body = FormBody.Builder().add("action", "manga_get_chapters").add("manga", mangaId).build()
                    items = Ksoup.parse(post("$site/wp-admin/admin-ajax.php", novelUrl, body), novelUrl).select(".wp-manga-chapter")
                }
            }
        }
        val chapters = items.mapNotNull { li ->
            if (li.hasClass("premium-block")) return@mapNotNull null
            val a = li.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href")
            if (href.isBlank() || href == "#") return@mapNotNull null
            ChapterInfo(key = abs(href), name = a.text().trim()).withAutoNumber()
        }.distinctBy { it.key }
        // The site lists the newest first
        return chapters.reversed()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val doc = fetch(chapter.key)
        val content = doc.selectFirst(".text-left") ?: doc.selectFirst(".text-right") ?: doc.selectFirst(".reading-content")
            ?: doc.selectFirst(".entry-content") ?: return emptyList()
        content.select("script, style, noscript, .code-block, .adsbygoogle, ins").remove()
        val paragraphs = content.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }
        val text = paragraphs.ifEmpty { content.text().split('\n').map { it.trim() }.filter { it.isNotEmpty() } }
        return listOf(Text(text.joinToString("\n")))
    }

    private companion object {
        val CHALLENGE_TITLES = setOf("Bot Verification", "You are being redirected...", "Un instant...", "Just a moment...", "Redirecting...", "Attention Required! | Cloudflare")
    }
}

/** Novel Nice (novelnice.com), asked for by the owner. */
class NovelNice : Madara(7_311_000_000_000_002L, "Novel Nice", "en", "https://novelnice.com")
