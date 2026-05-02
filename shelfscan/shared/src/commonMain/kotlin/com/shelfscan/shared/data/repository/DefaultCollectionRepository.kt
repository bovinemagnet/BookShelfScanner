package com.shelfscan.shared.data.repository

import com.shelfscan.shared.core.model.Collection
import com.shelfscan.shared.core.model.MediaItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory `CollectionRepository`. Reads and writes are serialised through a
 * `Mutex` so concurrent saves from review coroutines can't race on the
 * underlying map.
 */
class DefaultCollectionRepository : CollectionRepository {
    private val mutex = Mutex()
    private val collections = mutableMapOf<String, Collection>()

    override suspend fun saveCollection(collection: Collection) = mutex.withLock {
        collections[collection.id] = collection
    }

    override suspend fun getCollection(id: String): Collection? = mutex.withLock {
        collections[id]
    }

    override suspend fun saveItems(collectionId: String, items: List<MediaItem>) = mutex.withLock {
        val existing = collections[collectionId] ?: return@withLock
        collections[collectionId] = existing.copy(items = items)
    }

    override suspend fun getCollectionItems(collectionId: String): List<MediaItem> = mutex.withLock {
        collections[collectionId]?.items ?: emptyList()
    }

    override suspend fun getAllCollections(): List<Collection> = mutex.withLock {
        collections.values.toList()
    }
}
