package com.chowchin.r50.database

import androidx.room.*
import com.chowchin.r50.database.RowingDataPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface RowingDataPointDao {
    @Query("SELECT * FROM rowing_data_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getDataPointsBySession(sessionId: Long): Flow<List<RowingDataPoint>>

    @Query("SELECT * FROM rowing_data_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getDataPointsBySessionSync(sessionId: Long): List<RowingDataPoint>

    @Query("SELECT * FROM rowing_data_points WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestDataPoint(sessionId: Long): RowingDataPoint?

    @Query("SELECT COUNT(*) FROM rowing_data_points WHERE sessionId = :sessionId")
    suspend fun getDataPointCount(sessionId: Long): Int

    @Insert
    suspend fun insertDataPoint(dataPoint: RowingDataPoint)

    @Insert
    suspend fun insertDataPoints(dataPoints: List<RowingDataPoint>)

    @Delete
    suspend fun deleteDataPoint(dataPoint: RowingDataPoint)

    @Query("DELETE FROM rowing_data_points WHERE sessionId = :sessionId")
    suspend fun deleteDataPointsBySession(sessionId: Long)
}
