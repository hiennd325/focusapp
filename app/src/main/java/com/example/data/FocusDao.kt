package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): FocusSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession): Long

    @Update
    suspend fun updateSession(session: FocusSession)

    @Query("SELECT * FROM focus_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<FocusLockLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FocusLockLog): Long

    @Query("DELETE FROM focus_sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM focus_logs")
    suspend fun clearAllLogs()
}
