package com.shelfscan.shared.domain.export

import com.shelfscan.shared.core.model.MediaItem

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

    private fun toJson(items: List<MediaItem>): String {
        val entries = items.joinToString(",\n  ") { item ->
            """{"id":"${item.id}","mediaType":"${item.mediaType}","title":${item.title?.let { "\"${it.escapeJson()}\"" } ?: "null"},"creatorName":${item.creatorName?.let { "\"${it.escapeJson()}\"" } ?: "null"},"confidence":${item.confidence.value},"source":"${item.source}"}"""
        }
        return "[\n  $entries\n]"
    }

    private fun String.escapeCsv(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
