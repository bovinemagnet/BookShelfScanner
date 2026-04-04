package com.shelfscan.shared.data.repository

import com.shelfscan.shared.core.model.Collection
import com.shelfscan.shared.core.model.MediaItem

class DefaultCollectionRepository : CollectionRepository {
    private val collections = mutableMapOf<String, Collection>()

    override suspend fun saveCollection(collection: Collection) {
        collections[collection.id] = collection
    }

    override suspend fun getCollection(id: String): Collection? = collections[id]

    override suspend fun saveItems(collectionId: String, items: List<MediaItem>) {
        val collection = collections[collectionId] ?: return
        collections[collectionId] = collection.copy(items = items)
    }

    override suspend fun getCollectionItems(collectionId: String): List<MediaItem> =
        collections[collectionId]?.items ?: emptyList()

    override suspend fun getAllCollections(): List<Collection> = collections.values.toList()
}
