package com.shelfscan.shared.data.repository

import com.shelfscan.shared.core.model.ScanSession

class DefaultScanRepository : ScanRepository {
    private val sessions = mutableMapOf<String, ScanSession>()

    override suspend fun saveSession(session: ScanSession) {
        sessions[session.id] = session
    }

    override suspend fun getSession(id: String): ScanSession? = sessions[id]

    override suspend fun getAllSessions(): List<ScanSession> = sessions.values.toList()
}
