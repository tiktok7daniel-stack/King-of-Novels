package app.riwaya.engine

/** What kind of novels a source mostly has, for grouping on the Sources screen. */
enum class SourceKind { TRANSLATED, ORIGINAL, MACHINE, LIGHT_NOVEL }

/** The Arabic name, kind and site of each known source, by its short id (the middle of `ireader.<id>.<lang>`). */
data class SourceMeta(val arabicName: String, val kind: SourceKind, val site: String = "")

object SourceCatalog {

    private val arabic = mapOf(
        "kolnovel" to SourceMeta("ملوك الروايات", SourceKind.TRANSLATED, "https://kolnovel.com"),
        "freekolnovel" to SourceMeta("ملوك الروايات المجاني", SourceKind.TRANSLATED, "https://free.kolnovel.com"),
        "galaxynovels" to SourceMeta("مجرة الروايات", SourceKind.TRANSLATED, "https://galaxynovels.com"),
        "seanovel" to SourceMeta("بحر الروايات", SourceKind.TRANSLATED, "https://seanovel.org"),
        "mknov" to SourceMeta("مملكة الروايات", SourceKind.TRANSLATED, "https://mknov.com"),
        "mtlarabic" to SourceMeta("مكتبة الخيال", SourceKind.MACHINE, "https://mtlarabic.com"),
        "markazriwayat" to SourceMeta("مركز الروايات", SourceKind.TRANSLATED, "https://markazriwayat.com"),
        "novelparadise" to SourceMeta("جنة الروايات", SourceKind.TRANSLATED, "https://novelsparadise.site"),
        "rewayatfans" to SourceMeta("روايات فانز", SourceKind.TRANSLATED, "https://rewayatfans.com"),
        "rewayahfans" to SourceMeta("رواية فانز", SourceKind.TRANSLATED, "https://rewayahfans.net"),
        "rewayatclub" to SourceMeta("نادي الروايات", SourceKind.ORIGINAL, "https://rewayat.club"),
        "riwyat" to SourceMeta("روايات", SourceKind.TRANSLATED, "https://cenele.com"),
        "sunovels" to SourceMeta("صن نوفلز", SourceKind.TRANSLATED, "https://sunovels.com"),
        "realmnovel" to SourceMeta("عالم الروايات", SourceKind.ORIGINAL),
        "azorafly" to SourceMeta("أزورا فلاي", SourceKind.TRANSLATED, "https://azorafly.com"),
        "golden" to SourceMeta("الاستراحة الذهبية", SourceKind.TRANSLATED, "https://golden.rest"),
        "armtl" to SourceMeta("الترجمة الآلية العربية", SourceKind.MACHINE),
        "arnovel" to SourceMeta("الرواية العربية", SourceKind.TRANSLATED, "https://ar-no.com"),
        "hizomanga" to SourceMeta("هيزو", SourceKind.TRANSLATED, "https://hizomanga.net"),
        "novel4up" to SourceMeta("روايات فور أب", SourceKind.TRANSLATED, "https://novel4up.com"),
        "novelarab" to SourceMeta("روايات العرب", SourceKind.TRANSLATED),
    )

    /** `ireader.kolnovel.ar` gives `kolnovel`. */
    fun shortId(pkg: String): String = pkg.removePrefix("ireader.").substringBeforeLast('.').lowercase()

    fun meta(pkg: String): SourceMeta? = arabic[shortId(pkg)]

    /** The name shown for a source: Arabic for an Arabic source we know, else the extension's own name. */
    fun displayName(pkg: String, lang: String, name: String): String =
        if (lang == "ar") meta(pkg)?.arabicName ?: name else name

    fun kind(pkg: String, name: String): SourceKind {
        meta(pkg)?.let { return it.kind }
        val n = name.lowercase()
        return when {
            "mtl" in n || "machine" in n -> SourceKind.MACHINE
            "royalroad" in n || "scribble" in n || "wattpad" in n -> SourceKind.ORIGINAL
            "light" in n || "ln" == n -> SourceKind.LIGHT_NOVEL
            else -> SourceKind.TRANSLATED
        }
    }

    /** Language names, written in their own language. */
    val languageNames = linkedMapOf(
        "ar" to "العربية",
        "en" to "English",
        "zh" to "中文",
        "fr" to "Français",
        "ru" to "Русский",
        "in" to "Bahasa Indonesia",
        "tu" to "Türkçe",
        "es" to "Español",
        "pt" to "Português",
        "jp" to "日本語",
        "uk" to "Українська",
        "multi" to "Multi",
    )

    fun languageName(code: String): String = languageNames[code] ?: code.uppercase()
}
