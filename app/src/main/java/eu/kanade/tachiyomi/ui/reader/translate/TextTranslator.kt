package eu.kanade.tachiyomi.ui.reader.translate

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Translates a batch of strings, preserving order and count. */
interface TextTranslator {
    /** Provider and its non-secret settings; part of the cache key. */
    val signature: String

    suspend fun translate(texts: List<String>, source: String, target: String): List<String>
}

/** Prompt building and reply parsing shared by the LLM based providers. */
object LlmPrompt {

    fun system(source: String, target: String): String {
        val from = if (source == TranslationLanguages.AUTO) "the original language" else TranslationLanguages.nameOf(source)
        val to = TranslationLanguages.nameOf(target)
        return "You translate the speech balloons, captions and sound effects of one comic page " +
            "from $from to $to. The user message is a JSON array of strings in reading order. " +
            "Use the whole page as context for each item. Keep every translation short and natural " +
            "for a speech balloon. Reply with ONLY a JSON array of strings with exactly the same " +
            "number of items in the same order, no commentary and no code fences."
    }

    fun user(texts: List<String>): String = buildJsonArray { texts.forEach { add(JsonPrimitive(it)) } }.toString()

    /** Extracts the JSON array of strings from an LLM reply, tolerating code fences and prose around it. */
    fun parse(reply: String, expected: Int): List<String> {
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end <= start) throw PageTranslationException("The translation reply is not a list")
        val element = try {
            Json.parseToJsonElement(reply.substring(start, end + 1))
        } catch (e: Exception) {
            throw PageTranslationException("The translation reply is not valid JSON", e)
        }
        val items = (element as? JsonArray)?.map { it.jsonPrimitive.contentOrNull.orEmpty() }
            ?: throw PageTranslationException("The translation reply is not a list")
        if (items.size != expected) {
            throw PageTranslationException("The provider returned ${items.size} items instead of $expected")
        }
        return items
    }
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

private suspend fun OkHttpClient.postJson(request: Request.Builder, body: JsonObject): JsonObject {
    val req = request.post(body.toString().toRequestBody(JSON_MEDIA)).build()
    try {
        newCall(req).awaitSuccess().use { response ->
            val text = response.body.string()
            return Json.parseToJsonElement(text).jsonObject
        }
    } catch (e: HttpException) {
        val hint = when (e.code) {
            401, 403 -> "invalid API key or no access"
            429 -> "rate limit or quota exceeded"
            else -> "HTTP ${e.code}"
        }
        throw PageTranslationException("Translation provider error: $hint", e)
    } catch (e: IOException) {
        throw PageTranslationException("Network error: ${e.message}", e)
    } catch (e: PageTranslationException) {
        throw e
    } catch (e: Exception) {
        throw PageTranslationException("Unexpected provider reply: ${e.message}", e)
    }
}

/** Any endpoint that implements the OpenAI chat completions API (OpenAI, OpenRouter, NVIDIA, local servers, ...). */
class OpenAiCompatibleTranslator(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
) : TextTranslator {

    override val signature = "openai|${baseUrl.trimEnd('/')}|$model"

    override suspend fun translate(texts: List<String>, source: String, target: String): List<String> {
        if (apiKey.isBlank()) throw PageTranslationException("Enter the API key in the translation settings")
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", LlmPrompt.system(source, target))
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", LlmPrompt.user(texts))
                    },
                )
            }
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
        val reply = client.postJson(request, body)
        val content = reply["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw PageTranslationException("The provider returned no text")
        return LlmPrompt.parse(content, texts.size)
    }
}

class GeminiTranslator(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
) : TextTranslator {

    override val signature = "gemini|${baseUrl.trimEnd('/')}|$model"

    override suspend fun translate(texts: List<String>, source: String, target: String): List<String> {
        if (apiKey.isBlank()) throw PageTranslationException("Enter the API key in the translation settings")
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", LlmPrompt.system(source, target)) }) }
            }
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", LlmPrompt.user(texts)) }) }
                    },
                )
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            }
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
        val reply = client.postJson(request, body)
        val content = reply["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?: throw PageTranslationException("The provider returned no text")
        return LlmPrompt.parse(content, texts.size)
    }
}

/** Official DeepL API (the free plan key ends with ":fx"). */
class DeepLTranslator(
    private val client: OkHttpClient,
    private val apiKey: String,
) : TextTranslator {

    override val signature = "deepl|${endpoint(apiKey)}"

    override suspend fun translate(texts: List<String>, source: String, target: String): List<String> {
        if (apiKey.isBlank()) throw PageTranslationException("Enter the API key in the translation settings")
        val body = buildJsonObject {
            putJsonArray("text") { texts.forEach { add(JsonPrimitive(it)) } }
            put("target_lang", targetCode(target))
            if (source != TranslationLanguages.AUTO) put("source_lang", source.uppercase())
        }
        val request = Request.Builder()
            .url(endpoint(apiKey) + "/v2/translate")
            .header("Authorization", "DeepL-Auth-Key $apiKey")
        val reply = client.postJson(request, body)
        val items: List<String> = reply["translations"]?.jsonArray
            ?.map { (it as JsonElement).jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            ?: throw PageTranslationException("The provider returned no text")
        if (items.size != texts.size) {
            throw PageTranslationException("The provider returned ${items.size} items instead of ${texts.size}")
        }
        return items
    }

    companion object {
        fun endpoint(apiKey: String) = if (apiKey.endsWith(":fx")) "https://api-free.deepl.com" else "https://api.deepl.com"

        fun targetCode(code: String): String = when (code) {
            "en" -> "EN-US"
            "pt" -> "PT-BR"
            else -> code.uppercase()
        }
    }
}

