package app.riwaya.translate

import app.riwaya.core.Net
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An engine that cannot be used for the rest of this session (no key, quota used up, refused). */
class EngineOut(message: String) : IOException(message)

/** Google said "too many requests" (or sent its "unusual traffic" page): it gets a short rest. */
class GoogleBusy(val code: Int) : IOException("Google busy ($code)")

/**
 * Google Translate's free web addresses, no key: the one MANHWA uses (translate.google.com with its token) first,
 * then two others when one is refused. A refused address is asked again on the next one at once; when all of them
 * refuse, Google rests for half a minute and the next engine takes the text. At most 4 requests run at once.
 */
object GoogleFree {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val gate = kotlinx.coroutines.sync.Semaphore(4)
    @Volatile private var restUntil = 0L
    @Volatile private var preferred = 0
    @Volatile var lastError: String? = null
        private set

    // Redirects are not followed: Google's "unusual traffic" page comes as a redirect and means "too many"
    private val client by lazy { Net.client.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    private enum class Endpoint { WEB, APIS, DICT }

    fun resting(): Boolean = System.currentTimeMillis() < restUntil

    suspend fun translate(text: String, target: String, source: String = "auto"): String {
        if (text.isBlank()) return text
        if (resting()) throw GoogleBusy(429)
        return gate.withPermit {
            var last: Throwable? = null
            val all = Endpoint.entries
            for (attempt in 0 until all.size + 1) {
                val i = (preferred + attempt) % all.size
                try {
                    val out = withContext(Dispatchers.IO) { request(all[i], text, target, source) }
                    preferred = i
                    lastError = null
                    return@withPermit out
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    last = e
                    lastError = e.message
                    // A network error is worth one short wait; a refusal goes straight to the next address
                    if (e !is GoogleBusy) kotlinx.coroutines.delay(400L * (attempt + 1))
                }
            }
            if (last is GoogleBusy) restUntil = System.currentTimeMillis() + 30_000
            throw last ?: IOException("Google did not answer")
        }
    }

    private fun request(endpoint: Endpoint, text: String, target: String, source: String): String {
        val q = java.net.URLEncoder.encode(text, "UTF-8")
        val url = when (endpoint) {
            Endpoint.WEB -> "https://translate.google.com/translate_a/single?client=gtx&sl=$source&tl=$target&dt=t&ie=UTF-8&oe=UTF-8&otf=1&ssel=0&tsel=0&kc=1&tk=${token(text)}"
            Endpoint.APIS -> "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$source&tl=$target&dt=t&ie=UTF-8&oe=UTF-8"
            Endpoint.DICT -> "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$source&tl=$target"
        }
        // Short texts go in the address (as MANHWA sends them); long ones in the body
        val req = if (q.length < 5_000) {
            Request.Builder().url("$url&q=$q").get().build()
        } else {
            Request.Builder().url(url).post(FormBody.Builder().add("q", text).build()).build()
        }
        client.newCall(req).execute().use { r ->
            if (r.code == 429 || r.code == 403 || r.code in 300..399) throw GoogleBusy(r.code)
            if (!r.isSuccessful) throw IOException("Google HTTP ${r.code}")
            val body = r.body.string()
            if (body.trimStart().startsWith("<")) throw GoogleBusy(429)
            return parse(endpoint, body) ?: throw IOException("Google answer not understood")
        }
    }

    private fun parse(endpoint: Endpoint, body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonArray
        when (endpoint) {
            Endpoint.DICT -> {
                // ["text"] or [["text","en"]]
                val first = root.firstOrNull()
                ((first as? JsonArray)?.firstOrNull() ?: first)?.let { (it as? JsonPrimitive)?.contentOrNull }
            }
            else -> {
                val segments = root.firstOrNull() as? JsonArray ?: return@runCatching null
                buildString {
                    for (seg in segments) {
                        val p = (seg as? JsonArray)?.firstOrNull() as? JsonPrimitive ?: continue
                        if (p.isString) append(p.content)
                    }
                }
            }
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Several paragraphs in one call, split back by line; null when the lines do not come back one for one. */
    suspend fun translateLines(lines: List<String>, target: String, source: String = "auto"): List<String>? {
        val joined = lines.joinToString("\n") { it.replace('\n', ' ') }
        val out = translate(joined, target, source).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        return out.takeIf { it.size == lines.size }
    }

    /** The token translate.google.com expects with a text (the same one MANHWA sends). */
    private fun token(str: String): String {
        val list = mutableListOf<Int>()
        var i = 0
        while (i < str.length) {
            val c = str.codePointAt(i)
            when {
                c < 128 -> list.add(c)
                c < 2048 -> {
                    list.add((c shr 6) or 192)
                    list.add((c and 63) or 128)
                }
                c >= 65536 -> {
                    list.add((c shr 18) or 240)
                    list.add(((c shr 12) and 63) or 128)
                    list.add(((c shr 6) and 63) or 128)
                    list.add((c and 63) or 128)
                    i++
                }
                else -> {
                    list.add((c shr 12) or 224)
                    list.add(((c shr 6) and 63) or 128)
                    list.add((c and 63) or 128)
                }
            }
            i++
        }
        var j = 406644L
        for (n in list) j = rl(j + n.toLong(), "+-a^+6")
        var r = rl(j, "+-3^+b+-f") xor 3293161072L
        if (r < 0) r = (r and 2147483647L) + 2147483648L
        val j2 = r % 1000000L
        return "$j2.${406644L xor j2}"
    }

    private fun rl(j: Long, s: String): Long {
        var result = j
        var i = 0
        while (i < s.length - 2) {
            val shift = if (s[i + 2] in 'a'..'z') s[i + 2].code - 'W'.code else s[i + 2].digitToInt()
            val v = if (s[i + 1] == '+') result ushr shift else result shl shift
            result = if (s[i] == '+') (result + v) and 4294967295L else result xor v
            i += 3
        }
        return result
    }
}

/** Gemini with the reader's own key: literary translation that keeps the novel's names the same in every chapter. */
object Gemini {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Answer(val paragraphs: List<String>, val names: Map<String, String>)

    suspend fun translate(
        key: String,
        model: String,
        language: String,
        title: String,
        batch: List<String>,
        glossary: Map<String, String>,
    ): Answer? = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw EngineOut("No key")
        val system = "You translate web novels and light novels into natural, literary $language. " +
            "Translate every paragraph fully and faithfully, keep its tone and its dialogue marks, never summarise, " +
            "never merge or split paragraphs. Use the glossary for every name and term in it. " +
            "Reply with ONLY this JSON: {\"p\": [one translated string per paragraph, same count and order], " +
            "\"names\": {\"original name\": \"translated name\"} for new character, place and skill names}."
        val names = glossary.entries.take(MAX_GLOSSARY).joinToString("\n") { "${it.key} = ${it.value}" }
        val prompt = buildString {
            append("Novel: ").append(title).append('\n')
            if (names.isNotBlank()) append("Glossary:\n").append(names).append('\n')
            append("Paragraphs (JSON array):\n")
            append(JsonArray(batch.map { JsonPrimitive(it) }).toString())
        }
        val payload = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", system) }) } }
            putJsonArray("contents") {
                add(buildJsonObject { put("role", "user"); putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) } })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            }
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        Net.client.newCall(req).execute().use { r ->
            if (r.code == 429 || r.code == 401 || r.code == 403) throw EngineOut("Gemini ${r.code}")
            if (!r.isSuccessful) throw IOException("Gemini HTTP ${r.code}")
            val root = json.parseToJsonElement(r.body.string()).jsonObject
            val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                ?: return@use null
            val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val answer = runCatching { json.parseToJsonElement(clean).jsonObject }.getOrNull() ?: return@use null
            val list = (answer["p"] as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() } ?: return@use null
            if (list.size != batch.size || list.any { it.isBlank() }) return@use null
            val found = (answer["names"] as? JsonObject)?.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.takeIf { k.isNotBlank() && it.isNotBlank() }?.let { k to it }
            }?.toMap().orEmpty()
            Answer(list, found)
        }
    }

    private const val MAX_GLOSSARY = 150
}

/** ML Kit on the phone: works without internet once its language model is downloaded. */
object MlKit {

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    fun supports(code: String): Boolean = TranslateLanguage.fromLanguageTag(code) != null

    suspend fun translate(lines: List<String>, source: String, target: String): List<String> {
        val from = TranslateLanguage.fromLanguageTag(source) ?: throw EngineOut("ML Kit: no $source")
        val to = TranslateLanguage.fromLanguageTag(target) ?: throw EngineOut("ML Kit: no $target")
        val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return lines.map { line -> translator.translate(line).await() }
        } finally {
            translator.close()
        }
    }
}
