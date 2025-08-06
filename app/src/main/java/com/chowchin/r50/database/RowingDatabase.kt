package com.chowchin.r50.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RowingSession::class, RowingDataPoint::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class RowingDatabase : RoomDatabase() {
    abstract fun rowingSessionDao(): RowingSessionDao

    abstract fun rowingDataPointDao(): RowingDataPointDao

    companion object {
        @Volatile
        private var INSTANCE: RowingDatabase? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE rowing_sessions ADD COLUMN stravaUploadStatus TEXT NOT NULL DEFAULT 'NOT_UPLOADED'",
                    )
                    database.execSQL(
                        "ALTER TABLE rowing_sessions ADD COLUMN stravaActivityId INTEGER",
                    )
                    database.execSQL(
                        "ALTER TABLE rowing_sessions ADD COLUMN stravaUploadAttempts INTEGER NOT NULL DEFAULT 0",
                    )
                    database.execSQL(
                        "ALTER TABLE rowing_sessions ADD COLUMN lastStravaUploadAttempt INTEGER",
                    )
                }
            }

        fun getDatabase(context: Context): RowingDatabase =
            INSTANCE ?: synchronized(this) {
                val instance =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            RowingDatabase::class.java,
                            "rowing_database",
                        ).addMigrations(MIGRATION_1_2)
                        .build()
                INSTANCE = instance
                instance
            }
    }
}
