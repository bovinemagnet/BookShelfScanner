package com.shelfscan.shared.domain.export

import com.shelfscan.shared.core.model.MediaItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ExportCollectionUseCase {
    enum class ExportFormat { CSV, JSON }

    fun execute(items: List<MediaItem>, format: ExportFormat = ExportFormat.CSV): String {
        return when (format) {
            ExportFormat.CSV -> toCsv(items)
            ExportFormat.JSON -> toJson(items)
        }
    }

    private fun toCsv(items: List<MediaItem>): String {
        val header = "id,mediaType,title,creatorName,subtitle,confidence,source"
        val rows = items.map { item ->
            listOf(
                item.id.escapeCsv(),
                item.mediaType.name.escapeCsv(),
                (item.title ?: "").escapeCsv(),
                (item.creatorName ?: "").escapeCsv(),
                (item.subtitle ?: "").escapeCsv(),
                item.confidence.value.toString().escapeCsv(),
                item.source.name.escapeCsv()
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun toJson(items: List<MediaItem>): String =
        json.encodeToString(ListSerializer(MediaItemDto.serializer()), items.map { it.toDto() })

    private fun MediaItem.toDto() = MediaItemDto(
        id = id,
        mediaType = mediaType.name,
        title = title,
        creatorName = creatorName,
        confidence = confidence.value,
        source = source.name,
    )

    private fun String.escapeCsv(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }

    private companion object {
        // prettyPrint matches the previous output style closely while letting
        // kotlinx-serialization handle all RFC-8259 escaping correctly.
        val json = Json { prettyPrint = true; encodeDefaults = false }
    }

    /** Stable wire format for exported items — decoupled from the rich domain model. */
    @Serializable
    private data class MediaItemDto(
        val id: String,
        val mediaType: String,
        val title: String?,
        val creatorName: String?,
        val confidence: Double,
        val source: String,
    )
}
