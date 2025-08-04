package com.chowchin.r50.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [RowingSession::class, RowingDataPoint::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RowingDatabase : RoomDatabase() {
    abstract fun rowingSessionDao(): RowingSessionDao
    abstract fun rowingDataPointDao(): RowingDataPointDao

    companion object {
        @Volatile
        private var INSTANCE: RowingDatabase? = null

        fun getDatabase(context: Context): RowingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RowingDatabase::class.java,
                    "rowing_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
