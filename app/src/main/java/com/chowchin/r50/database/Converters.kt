package com.chowchin.r50.database

import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromStravaUploadStatus(status: StravaUploadStatus): String = status.name

    @TypeConverter
    fun toStravaUploadStatus(status: String): StravaUploadStatus = StravaUploadStatus.valueOf(status)
}
