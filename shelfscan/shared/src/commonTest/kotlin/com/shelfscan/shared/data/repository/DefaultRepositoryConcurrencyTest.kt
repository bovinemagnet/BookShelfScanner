package com.shelfscan.shared.data.repository

import com.shelfscan.shared.core.model.Collection
import com.shelfscan.shared.core.model.ImageQualityAssessment
import com.shelfscan.shared.core.model.ScanSession
import com.shelfscan.shared.core.model.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Hammers the in-memory repositories from a multi-threaded dispatcher to
 * confirm that the Mutex wrap actually protects against lost writes. With the
 * unprotected `mutableMapOf` these tests fail intermittently (or
 * `ConcurrentModificationException` is thrown).
 */
class DefaultRepositoryConcurrencyTest {

    private val concurrentWriters = 100

    @Test
    fun `concurrent saveSession does not lose writes`() = runBlocking {
        val repo = DefaultScanRepository()
        coroutineScope {
            (1..concurrentWriters).map { i ->
                async(Dispatchers.Default) { repo.saveSession(makeSession("session_$i")) }
            }.awaitAll()
        }
        assertEquals(concurrentWriters, repo.getAllSessions().size)
    }

    @Test
    fun `concurrent saveCollection does not lose writes`() = runBlocking {
        val repo = DefaultCollectionRepository()
        coroutineScope {
            (1..concurrentWriters).map { i ->
                async(Dispatchers.Default) {
                    repo.saveCollection(Collection(id = "c_$i", name = "C$i", createdAt = 0L))
                }
            }.awaitAll()
        }
        assertEquals(concurrentWriters, repo.getAllCollections().size)
    }

    private fun makeSession(id: String) = ScanSession(
        id = id,
        createdAt = 0L,
        sourceImageRef = "test.jpg",
        quality = ImageQualityAssessment(
            blurScore = 0.0,
            brightness = 0.5,
            isAcceptable = true,
            reasons = emptyList()
        ),
        status = ScanStatus.COMPLETE,
    )
}
