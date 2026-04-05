package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.CatalogMatch
import com.shelfscan.shared.core.model.MediaType

class NoOpMetadataLookupService : MetadataLookupService {
    override suspend fun search(
        mediaType: MediaType,
        title: String?,
        creatorName: String?
    ): List<CatalogMatch> = emptyList()
}
