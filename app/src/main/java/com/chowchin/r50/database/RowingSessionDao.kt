package com.chowchin.r50.database

import androidx.room.*
import com.chowchin.r50.database.RowingSession
import com.chowchin.r50.database.RowingDataPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface RowingSessionDao {
    @Query("SELECT * FROM rowing_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<RowingSession>>

    @Query("SELECT * FROM rowing_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): RowingSession?

    @Query("SELECT * FROM rowing_sessions WHERE isCompleted = 0 LIMIT 1")
    suspend fun getActiveSession(): RowingSession?

    @Insert
    suspend fun insertSession(session: RowingSession): Long

    @Update
    suspend fun updateSession(session: RowingSession)

    @Delete
    suspend fun deleteSession(session: RowingSession)

    @Query("DELETE FROM rowing_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}
