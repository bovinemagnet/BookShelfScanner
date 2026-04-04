package com.shelfscan.shared.data.repository

import com.shelfscan.shared.core.model.ScanSession

interface ScanRepository {
    suspend fun saveSession(session: ScanSession)
    suspend fun getSession(id: String): ScanSession?
    suspend fun getAllSessions(): List<ScanSession>
}
