package com.chowchin.r50.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "rowing_sessions")
data class RowingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Date,
    val endTime: Date? = null,
    val totalDuration: Int = 0, // in seconds
    val totalDistance: Int = 0, // in meters
    val totalStrokes: Int = 0,
    val totalCalories: Int = 0,
    val averageSPM: Float = 0f,
    val averagePower: Float = 0f,
    val maxPower: Int = 0,
    val isCompleted: Boolean = false,
    val stravaUploadStatus: StravaUploadStatus = StravaUploadStatus.NOT_UPLOADED,
    val stravaActivityId: Long? = null,
    val stravaUploadAttempts: Int = 0,
    val lastStravaUploadAttempt: Date? = null,
)

enum class StravaUploadStatus {
    NOT_UPLOADED,
    UPLOADING,
    UPLOADED,
    FAILED,
}
