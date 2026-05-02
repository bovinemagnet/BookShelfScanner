package com.shelfscan.shared.data.metadata

import com.shelfscan.shared.core.model.CatalogMatch
import com.shelfscan.shared.core.model.CatalogSource
import com.shelfscan.shared.core.model.MediaType
import com.shelfscan.shared.platform.MetadataLookupService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Looks up books in the Open Library API.
 *
 * Request timeouts are the responsibility of the supplied `client` — install
 * the `HttpTimeout` plugin with sane bounds at construction time, e.g.
 * `install(HttpTimeout) { requestTimeoutMillis = 5_000 }`. We can't enforce a
 * timeout from inside this class portably (kotlinx.coroutines.withTimeout
 * misbehaves under runTest's virtual time, and Ktor's per-request timeout DSL
 * needs the plugin installed regardless).
 */
class OpenLibraryMetadataLookupService(
    private val client: HttpClient,
    private val baseUrl: String = "https://openlibrary.org",
    private val resultLimit: Int = 5,
    private val userAgent: String = DEFAULT_USER_AGENT,
) : MetadataLookupService {

    override suspend fun search(
        mediaType: MediaType,
        title: String?,
        creatorName: String?,
    ): List<CatalogMatch> {
        if (mediaType != MediaType.BOOK) return emptyList()
        val titleQ = title?.trim().orEmpty()
        val creatorQ = creatorName?.trim().orEmpty()
        if (titleQ.isEmpty() && creatorQ.isEmpty()) return emptyList()

        val response: HttpResponse = try {
            client.get("$baseUrl/search.json") {
                header(HttpHeaders.UserAgent, userAgent)
                if (titleQ.isNotEmpty()) url.parameters.append("title", titleQ)
                if (creatorQ.isNotEmpty()) url.parameters.append("author", creatorQ)
                url.parameters.append("limit", resultLimit.toString())
            }
        } catch (e: Throwable) {
            // Network failure, timeout, host unreachable — degrade gracefully.
            return emptyList()
        }

        if (!response.status.isSuccess()) return emptyList()

        val payload: OpenLibrarySearchResponse = try {
            response.body()
        } catch (e: Throwable) {
            return emptyList()
        }

        return payload.docs.map { doc ->
            CatalogMatch(
                source = CatalogSource(SOURCE_NAME),
                mediaType = MediaType.BOOK,
                title = doc.title.orEmpty(),
                creatorName = doc.authorName?.firstOrNull(),
                year = doc.firstPublishYear,
                score = scoreMatch(
                    queryTitle = titleQ,
                    queryAuthor = creatorQ,
                    docTitle = doc.title.orEmpty(),
                    docAuthor = doc.authorName?.firstOrNull().orEmpty(),
                ),
                externalId = doc.key.orEmpty(),
            )
        }
    }

    /**
     * Score a candidate by token-Jaccard similarity against the query.
     * Title carries 70% of the weight, author 30% — and only if the user
     * supplied an author query, otherwise we score on title alone.
     */
    private fun scoreMatch(
        queryTitle: String,
        queryAuthor: String,
        docTitle: String,
        docAuthor: String,
    ): Double {
        val titleSim = jaccard(tokenize(queryTitle), tokenize(docTitle))
        if (queryAuthor.isBlank()) return titleSim
        val authorSim = jaccard(tokenize(queryAuthor), tokenize(docAuthor))
        return TITLE_WEIGHT * titleSim + AUTHOR_WEIGHT * authorSim
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase().split(TOKEN_SEPARATOR).filter { it.isNotBlank() }.toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        return intersection.toDouble() / union.toDouble()
    }

    companion object {
        const val SOURCE_NAME = "OpenLibrary"
        const val DEFAULT_USER_AGENT = "ShelfScan/1.0 (https://github.com/bovinemagnet/BookShelfScanner)"

        private const val TITLE_WEIGHT = 0.7
        private const val AUTHOR_WEIGHT = 0.3
        private val TOKEN_SEPARATOR = Regex("[\\s\\p{Punct}]+")
    }
}

@Serializable
private data class OpenLibrarySearchResponse(
    val docs: List<OpenLibraryDoc> = emptyList()
)

@Serializable
private data class OpenLibraryDoc(
    val title: String? = null,
    @SerialName("author_name") val authorName: List<String>? = null,
    @SerialName("first_publish_year") val firstPublishYear: Int? = null,
    val key: String? = null,
)
