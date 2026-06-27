package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val plannedDurationMinutes: Int,
    val endTime: Long = 0,
    val status: String, // "ACTIVE", "COMPLETED", "ABANDONED"
    val distractionCount: Int = 0,
    val unlockChallengeType: String? = null
)
