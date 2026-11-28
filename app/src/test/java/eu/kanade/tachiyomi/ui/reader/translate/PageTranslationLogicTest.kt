package eu.kanade.tachiyomi.ui.reader.translate

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PageTranslationLogicTest {

    @Test
    fun `script detector separates Japanese Korean Chinese and Latin`() {
        ScriptDetector.detect("こんにちは、世界") shouldBe Script.JAPANESE
        ScriptDetector.detect("안녕하세요") shouldBe Script.KOREAN
        ScriptDetector.detect("你好世界") shouldBe Script.CHINESE
        ScriptDetector.detect("Hello world") shouldBe Script.LATIN
        ScriptDetector.detect("123 !!") shouldBe Script.OTHER
    }

    @Test
    fun `llm reply parser accepts fences and prose around the array`() {
        val reply = "Here you go:\n```json\n[\"a\", \"b\"]\n```"
        LlmPrompt.parse(reply, 2) shouldBe listOf("a", "b")
    }

    @Test
    fun `llm reply parser rejects wrong item count and non json`() {
        shouldThrow<PageTranslationException> { LlmPrompt.parse("[\"a\"]", 2) }
        shouldThrow<PageTranslationException> { LlmPrompt.parse("no list here", 1) }
    }

    @Test
    fun `llm user message is a json array that preserves quotes and newlines`() {
        val texts = listOf("He said \"hi\"", "line1\nline2")
        val parsed = Json.parseToJsonElement(LlmPrompt.user(texts)).jsonArray.map { it.jsonPrimitive.content }
        parsed shouldBe texts
    }

    @Test
    fun `cache key changes with every input that affects the output`() {
        val style = RenderStyle()
        fun key(
            hash: String = "h",
            ocr: String = "o1",
            source: String = "ja",
            target: String = "en",
            provider: String = "p",
            render: Int = 1,
            style: RenderStyle = RenderStyle(),
        ) = TranslatedPageCache.key(hash, ocr, source, target, provider, render, style)

        val base = key()
        key() shouldBe base
        listOf(
            key(hash = "h2"),
            key(ocr = "o2"),
            key(source = "ko"),
            key(target = "ar"),
            key(provider = "p2"),
            key(render = 2),
            key(style = RenderStyle(fontScale = 1.25f)),
            key(style = RenderStyle(textColor = TextColorMode.WHITE)),
        ).forEach { it shouldNotBe base }
        style shouldBe RenderStyle()
    }

    @Test
    fun `cache stores reads and evicts least recently used files`(@TempDir dir: File) {
        val cache = TranslatedPageCache(File(dir, "c"), maxBytes = { 250L })
        cache.put("a", ByteArray(100) { 1 })
        File(dir, "c/a").setLastModified(1_000)
        cache.put("b", ByteArray(100) { 2 })
        File(dir, "c/b").setLastModified(2_000)
        cache.get("a") shouldNotBe null
        cache.put("c", ByteArray(100) { 3 })
        // 300 bytes > 250: the oldest file is removed, the freshly read one survives.
        cache.get("b") shouldBe null
        cache.get("a") shouldNotBe null
        cache.get("c") shouldNotBe null
        cache.clear()
        cache.sizeBytes() shouldBe 0L
    }

    @Test
    fun `openai compatible translator sends key and returns parsed items`() = runBlocking {
        var seen: Request? = null
        var body = ""
        val client = fakeClient { request ->
            seen = request
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            body = buffer.readUtf8()
            """{"choices":[{"message":{"content":"[\"Hello\",\"Bye\"]"}}]}"""
        }
        val translator = OpenAiCompatibleTranslator(client, "https://example.test/v1/", "m1", "KEY")
        val out = translator.translate(listOf("こんにちは", "さようなら"), "ja", "en")

        out shouldBe listOf("Hello", "Bye")
        seen!!.url.toString() shouldBe "https://example.test/v1/chat/completions"
        seen!!.header("Authorization") shouldBe "Bearer KEY"
        val json = Json.parseToJsonElement(body).jsonObject
        json["model"]!!.jsonPrimitive.content shouldBe "m1"
        json["messages"]!!.jsonArray.size shouldBe 2
    }

    @Test
    fun `gemini translator uses header key and reads candidate text`() = runBlocking {
        var seen: Request? = null
        val client = fakeClient { request ->
            seen = request
            """{"candidates":[{"content":{"parts":[{"text":"[\"Hi\"]"}]}}]}"""
        }
        val translator = GeminiTranslator(client, "https://g.test/v1beta", "gem", "GKEY")
        translator.translate(listOf("やあ"), "auto", "en") shouldBe listOf("Hi")
        seen!!.url.toString() shouldBe "https://g.test/v1beta/models/gem:generateContent"
        seen!!.header("x-goog-api-key") shouldBe "GKEY"
    }

    @Test
    fun `deepl translator picks endpoint by key and maps language codes`() = runBlocking {
        DeepLTranslator.endpoint("abc:fx") shouldBe "https://api-free.deepl.com"
        DeepLTranslator.endpoint("abc") shouldBe "https://api.deepl.com"
        DeepLTranslator.targetCode("en") shouldBe "EN-US"
        DeepLTranslator.targetCode("ar") shouldBe "AR"

        var seen: Request? = null
        val client = fakeClient { request ->
            seen = request
            """{"translations":[{"text":"Hello"}]}"""
        }
        DeepLTranslator(client, "abc:fx").translate(listOf("Hallo"), "auto", "en") shouldBe listOf("Hello")
        seen!!.header("Authorization") shouldBe "DeepL-Auth-Key abc:fx"
    }

    @Test
    fun `providers report a clear error without a key or with a rejected key`() {
        runBlocking {
            val noKey = OpenAiCompatibleTranslator(fakeClient { "" }, "https://x.test", "m", "")
            shouldThrow<PageTranslationException> { noKey.translate(listOf("a"), "ja", "en") }

            val rejecting = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain -> respond(chain.request(), 401, "{}") },
            ).build()
            val bad = OpenAiCompatibleTranslator(rejecting, "https://x.test", "m", "K")
            val error = shouldThrow<PageTranslationException> { bad.translate(listOf("a"), "ja", "en") }
            error.message!!.contains("API key") shouldBe true
        }
    }

    private fun fakeClient(reply: (Request) -> String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain -> respond(chain.request(), 200, reply(chain.request())) })
        .build()

    private fun respond(request: Request, code: Int, body: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}
