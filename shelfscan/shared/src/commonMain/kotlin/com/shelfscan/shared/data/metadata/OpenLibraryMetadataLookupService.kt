package com.shelfscan.shared.data.metadata

import com.shelfscan.shared.core.model.CatalogMatch
import com.shelfscan.shared.core.model.CatalogSource
import com.shelfscan.shared.core.model.MediaType
import com.shelfscan.shared.platform.MetadataLookupService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OpenLibraryMetadataLookupService(
    private val client: HttpClient,
    private val baseUrl: String = "https://openlibrary.org",
    private val resultLimit: Int = 5,
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
                if (titleQ.isNotEmpty()) url.parameters.append("title", titleQ)
                if (creatorQ.isNotEmpty()) url.parameters.append("author", creatorQ)
                url.parameters.append("limit", resultLimit.toString())
            }
        } catch (e: Throwable) {
            return emptyList()
        }

        if (!response.status.isSuccess()) return emptyList()

        val payload: OpenLibrarySearchResponse = try {
            response.body()
        } catch (e: Throwable) {
            return emptyList()
        }

        return payload.docs.mapIndexed { index, doc ->
            CatalogMatch(
                source = CatalogSource(SOURCE_NAME),
                mediaType = MediaType.BOOK,
                title = doc.title.orEmpty(),
                creatorName = doc.authorName?.firstOrNull(),
                year = doc.firstPublishYear,
                score = (1.0 - index * 0.1).coerceAtLeast(0.0),
                externalId = doc.key.orEmpty(),
            )
        }
    }

    companion object {
        const val SOURCE_NAME = "OpenLibrary"
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
