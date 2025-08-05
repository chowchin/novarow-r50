package com.chowchin.r50.database

import androidx.room.*
import com.chowchin.r50.database.RowingSession
import com.chowchin.r50.database.RowingDataPoint
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface RowingSessionDao {
    @Query("SELECT * FROM rowing_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<RowingSession>>

    @Query("SELECT * FROM rowing_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): RowingSession?

    @Query("SELECT * FROM rowing_sessions WHERE isCompleted = 0 LIMIT 1")
    suspend fun getActiveSession(): RowingSession?
    
    @Query("SELECT * FROM rowing_sessions WHERE isCompleted = 1 AND stravaUploadStatus = 'NOT_UPLOADED' ORDER BY endTime DESC")
    suspend fun getUnuploadedSessions(): List<RowingSession>
    
    @Query("SELECT * FROM rowing_sessions WHERE stravaUploadStatus = 'FAILED' AND stravaUploadAttempts < 3 ORDER BY lastStravaUploadAttempt ASC")
    suspend fun getFailedUploadSessions(): List<RowingSession>

    @Insert
    suspend fun insertSession(session: RowingSession): Long

    @Update
    suspend fun updateSession(session: RowingSession)
    
    @Query("UPDATE rowing_sessions SET stravaUploadStatus = :status, stravaActivityId = :activityId, stravaUploadAttempts = stravaUploadAttempts + 1, lastStravaUploadAttempt = :timestamp WHERE id = :sessionId")
    suspend fun updateStravaUploadStatus(sessionId: Long, status: StravaUploadStatus, activityId: Long? = null, timestamp: Date = Date())

    @Delete
    suspend fun deleteSession(session: RowingSession)

    @Query("DELETE FROM rowing_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}
