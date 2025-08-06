package com.chowchin.r50.database

import kotlinx.coroutines.flow.Flow

class RowingRepository(
    private val sessionDao: RowingSessionDao,
    private val dataPointDao: RowingDataPointDao,
) {
    // Session operations
    fun getAllSessions(): Flow<List<RowingSession>> = sessionDao.getAllSessions()

    suspend fun getSessionById(sessionId: Long): RowingSession? = sessionDao.getSessionById(sessionId)

    suspend fun getActiveSession(): RowingSession? = sessionDao.getActiveSession()

    suspend fun createNewSession(): Long {
        val session =
            RowingSession(
                startTime = java.util.Date(),
                isCompleted = false,
            )
        return sessionDao.insertSession(session)
    }

    suspend fun updateSession(session: RowingSession) = sessionDao.updateSession(session)

    suspend fun completeSession(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId)
        session?.let {
            // Calculate session statistics from data points
            val dataPoints = dataPointDao.getDataPointsBySession(sessionId)
            var totalDistance = 0
            var totalStrokes = 0
            var totalCalories = 0
            var powerSum = 0f
            var spmSum = 0f
            var maxPower = 0
            var dataPointCount = 0

            // Since we can't use collect in suspend function, we need to get the latest values
            val latestDataPoint = dataPointDao.getLatestDataPoint(sessionId)

            // Use the latest data point for totals (as these are cumulative in rowing machines)
            latestDataPoint?.let { latest ->
                totalDistance = latest.distance ?: 0
                totalStrokes = latest.strokeCount ?: 0
                totalCalories = latest.calories ?: 0
            }

            val endTime = java.util.Date()
            val duration = ((endTime.time - it.startTime.time) / 1000).toInt()

            val updatedSession =
                it.copy(
                    endTime = endTime,
                    totalDuration = duration,
                    totalDistance = totalDistance,
                    totalStrokes = totalStrokes,
                    totalCalories = totalCalories,
                    isCompleted = true,
                )
            sessionDao.updateSession(updatedSession)
        }
    }

    suspend fun deleteSession(sessionId: Long) = sessionDao.deleteSessionById(sessionId)

    // Data point operations
    fun getDataPointsBySession(sessionId: Long): Flow<List<RowingDataPoint>> = dataPointDao.getDataPointsBySession(sessionId)

    suspend fun getDataPointsBySessionSync(sessionId: Long): List<RowingDataPoint> = dataPointDao.getDataPointsBySessionSync(sessionId)

    suspend fun getLatestDataPoint(sessionId: Long): RowingDataPoint? = dataPointDao.getLatestDataPoint(sessionId)

    suspend fun addDataPoint(
        sessionId: Long,
        dataPoint: RowingDataPoint,
    ) {
        val point = dataPoint.copy(sessionId = sessionId)
        dataPointDao.insertDataPoint(point)
    }

    suspend fun getDataPointCount(sessionId: Long): Int = dataPointDao.getDataPointCount(sessionId)

    // Strava operations
    suspend fun getUnuploadedSessions(): List<RowingSession> = sessionDao.getUnuploadedSessions()

    suspend fun getFailedUploadSessions(): List<RowingSession> = sessionDao.getFailedUploadSessions()

    suspend fun updateSessionStravaStatus(
        sessionId: Long,
        status: StravaUploadStatus,
    ) {
        sessionDao.updateStravaUploadStatus(sessionId, status)
    }

    suspend fun updateSessionStravaUpload(
        sessionId: Long,
        status: StravaUploadStatus,
        activityId: Long?,
        attempts: Int,
    ) {
        sessionDao.updateStravaUploadStatus(sessionId, status, activityId)
    }
}
