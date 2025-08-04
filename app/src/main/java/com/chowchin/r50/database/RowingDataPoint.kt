package com.chowchin.r50.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "rowing_data_points",
    foreignKeys = [
        ForeignKey(
            entity = RowingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RowingDataPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Date,
    val elapsedSecond: Int?,
    val strokeCount: Int?,
    val strokePerMinute: Int?,
    val distance: Int?,
    val calories: Int?,
    val heartbeat: Int?,
    val power: Int?,
    val gear: Int?,
    val rawHex: String
)
