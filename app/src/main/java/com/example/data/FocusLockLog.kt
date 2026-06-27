package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_logs")
data class FocusLockLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "LOCK_START", "LOCK_END", "DISTRACTION", "CHALLENGE_COMPLETED", "CHALLENGE_FAILED", "AI_ANALYSIS"
    val details: String
)
