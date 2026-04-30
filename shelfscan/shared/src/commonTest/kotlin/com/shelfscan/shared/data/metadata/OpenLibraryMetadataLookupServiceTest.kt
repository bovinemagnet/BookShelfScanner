package com.shelfscan.shared.data.metadata

import com.shelfscan.shared.core.model.MediaType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenLibraryMetadataLookupServiceTest {

    private fun serviceWith(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{"numFound":0,"docs":[]}""",
        onRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ): OpenLibraryMetadataLookupService {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return OpenLibraryMetadataLookupService(client)
    }

    @Test
    fun `parses docs into CatalogMatch list`() = runTest {
        val body = """
            {
              "numFound": 2,
              "docs": [
                {"title":"Clean Code","author_name":["Robert C. Martin"],"first_publish_year":2008,"key":"/works/OL2388307W"},
                {"title":"Clean Coder","author_name":["Robert C. Martin"],"first_publish_year":2011,"key":"/works/OL99999W"}
              ]
            }
        """.trimIndent()
        val service = serviceWith(body = body)

        val matches = service.search(MediaType.BOOK, title = "Clean Code", creatorName = null)

        assertEquals(2, matches.size)
        assertEquals("Clean Code", matches[0].title)
        assertEquals("Robert C. Martin", matches[0].creatorName)
        assertEquals(2008, matches[0].year)
        assertEquals("/works/OL2388307W", matches[0].externalId)
        assertEquals(MediaType.BOOK, matches[0].mediaType)
        assertEquals("OpenLibrary", matches[0].source.name)
        assertTrue(matches[0].score > matches[1].score, "first result should outrank second")
    }

    @Test
    fun `returns empty list on non-success response`() = runTest {
        val service = serviceWith(status = HttpStatusCode.InternalServerError, body = "boom")

        val matches = service.search(MediaType.BOOK, title = "anything", creatorName = null)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `returns empty list when title and creator are both blank`() = runTest {
        var called = false
        val service = serviceWith(onRequest = { called = true })

        val matches = service.search(MediaType.BOOK, title = "  ", creatorName = null)

        assertTrue(matches.isEmpty())
        assertTrue(!called, "no HTTP call should be made when query is empty")
    }

    @Test
    fun `returns empty list for non-BOOK media types`() = runTest {
        var called = false
        val service = serviceWith(onRequest = { called = true })

        val matches = service.search(MediaType.MOVIE, title = "Inception", creatorName = null)

        assertTrue(matches.isEmpty())
        assertTrue(!called, "OpenLibrary should not be queried for movies")
    }

    @Test
    fun `passes title and author as query parameters`() = runTest {
        var capturedUrl: String? = null
        val service = serviceWith(
            body = """{"numFound":0,"docs":[]}""",
            onRequest = { capturedUrl = it.url.toString() }
        )

        service.search(MediaType.BOOK, title = "Clean Code", creatorName = "Martin")

        val url = capturedUrl ?: error("request not made")
        assertTrue(url.contains("openlibrary.org"), "url was $url")
        assertTrue(url.contains("search.json"), "url was $url")
        assertTrue(url.contains("title=Clean"), "url was $url")
        assertTrue(url.contains("author=Martin"), "url was $url")
    }

    @Test
    fun `tolerates missing optional fields`() = runTest {
        val body = """
            {"numFound":1,"docs":[{"title":"Mystery Book","key":"/works/OLX"}]}
        """.trimIndent()
        val service = serviceWith(body = body)

        val matches = service.search(MediaType.BOOK, title = "Mystery Book", creatorName = null)

        assertEquals(1, matches.size)
        assertEquals("Mystery Book", matches[0].title)
        assertNull(matches[0].creatorName)
        assertNull(matches[0].year)
        assertEquals("/works/OLX", matches[0].externalId)
    }

    @Test
    fun `returns empty list when HTTP call throws`() = runTest {
        val engine = MockEngine { throw kotlinx.io.IOException("network down") }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val service = OpenLibraryMetadataLookupService(client)

        val matches = service.search(MediaType.BOOK, title = "Clean Code", creatorName = null)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `returns empty list when response body is malformed JSON`() = runTest {
        val service = serviceWith(body = "this is not json at all")

        val matches = service.search(MediaType.BOOK, title = "Clean Code", creatorName = null)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `score reflects similarity to query title, not API position`() = runTest {
        // Three docs returned in API order. The closest token match to "Clean Code"
        // is the second doc, not the first — score must reflect that.
        val body = """
            {"numFound":3,"docs":[
              {"title":"Cleaning Up","key":"/works/OL1"},
              {"title":"Clean Code","key":"/works/OL2"},
              {"title":"Cleanliness","key":"/works/OL3"}
            ]}
        """.trimIndent()
        val service = serviceWith(body = body)

        val matches = service.search(MediaType.BOOK, title = "Clean Code", creatorName = null)

        assertEquals(3, matches.size)
        val cleanCode = matches.first { it.title == "Clean Code" }
        val others = matches.filter { it.title != "Clean Code" }
        others.forEach { weaker ->
            assertTrue(
                cleanCode.score > weaker.score,
                "expected '${cleanCode.title}' (${cleanCode.score}) > '${weaker.title}' (${weaker.score})"
            )
        }
    }

    @Test
    fun `scores are bounded between zero and one`() = runTest {
        val docs = (1..12).joinToString(",") { i ->
            """{"title":"Book $i","key":"/works/OL$i"}"""
        }
        val service = OpenLibraryMetadataLookupService(
            client = HttpClient(MockEngine {
                respond(
                    content = ByteReadChannel("""{"numFound":12,"docs":[$docs]}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            resultLimit = 20
        )

        val matches = service.search(MediaType.BOOK, title = "Book", creatorName = null)
        matches.forEach {
            assertTrue(it.score in 0.0..1.0, "score must be in [0, 1], got ${it.score}")
        }
    }

    @Test
    fun `request carries a User-Agent header`() = runTest {
        var capturedUa: String? = null
        val service = serviceWith(onRequest = {
            capturedUa = it.headers[HttpHeaders.UserAgent]
        })

        service.search(MediaType.BOOK, title = "anything", creatorName = null)

        assertNotNull(capturedUa, "User-Agent header must be set")
        assertTrue(
            capturedUa!!.contains("ShelfScan", ignoreCase = true),
            "User-Agent must identify the app: $capturedUa"
        )
    }

    @Test
    fun `slow request times out via HttpTimeout plugin and returns empty list`() = runTest {
        val engine = MockEngine {
            kotlinx.coroutines.delay(60_000) // far longer than the configured timeout
            respond(content = ByteReadChannel("{\"docs\":[]}"), status = HttpStatusCode.OK)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 50
            }
        }
        val service = OpenLibraryMetadataLookupService(client = client)

        val matches = service.search(MediaType.BOOK, title = "Clean Code", creatorName = null)

        assertTrue(matches.isEmpty(), "expected empty list on timeout, got $matches")
    }
}
