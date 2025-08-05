package com.chowchin.r50.database

import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun fromStravaUploadStatus(status: StravaUploadStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toStravaUploadStatus(status: String): StravaUploadStatus {
        return StravaUploadStatus.valueOf(status)
    }
}
