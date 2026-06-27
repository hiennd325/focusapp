package com.example.data

import kotlinx.coroutines.flow.Flow

class FocusRepository(private val focusDao: FocusDao) {
    val allSessions: Flow<List<FocusSession>> = focusDao.getAllSessions()
    val allLogs: Flow<List<FocusLockLog>> = focusDao.getAllLogs()

    suspend fun getActiveSession(): FocusSession? {
        return focusDao.getActiveSession()
    }

    suspend fun startSession(plannedDurationMinutes: Int): Long {
        val session = FocusSession(
            startTime = System.currentTimeMillis(),
            plannedDurationMinutes = plannedDurationMinutes,
            status = "ACTIVE"
        )
        return focusDao.insertSession(session)
    }

    suspend fun updateSession(session: FocusSession) {
        focusDao.updateSession(session)
    }

    suspend fun logEvent(eventType: String, details: String) {
        focusDao.insertLog(FocusLockLog(eventType = eventType, details = details))
    }

    suspend fun clearData() {
        focusDao.clearAllSessions()
        focusDao.clearAllLogs()
    }
}
