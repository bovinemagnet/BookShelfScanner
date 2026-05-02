package com.shelfscan.shared.data.repository

import com.shelfscan.shared.core.model.ScanSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory `ScanRepository`. Reads and writes are serialised through a
 * `Mutex` so concurrent calls from camera, OCR, and review coroutines can't
 * race on the underlying map.
 */
class DefaultScanRepository : ScanRepository {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, ScanSession>()

    override suspend fun saveSession(session: ScanSession) = mutex.withLock {
        sessions[session.id] = session
    }

    override suspend fun getSession(id: String): ScanSession? = mutex.withLock {
        sessions[id]
    }

    override suspend fun getAllSessions(): List<ScanSession> = mutex.withLock {
        sessions.values.toList()
    }
}
