package com.shelfscan.shared.data.repository

import com.shelfscan.shared.core.model.Collection
import com.shelfscan.shared.core.model.MediaItem

interface CollectionRepository {
    suspend fun saveCollection(collection: Collection)
    suspend fun getCollection(id: String): Collection?
    suspend fun saveItems(collectionId: String, items: List<MediaItem>)
    suspend fun getCollectionItems(collectionId: String): List<MediaItem>
    suspend fun getAllCollections(): List<Collection>
}
