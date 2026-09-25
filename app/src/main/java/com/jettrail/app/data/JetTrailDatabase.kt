package com.jettrail.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FlightEntity::class, FlightSampleEntity::class, EarnedBadgeEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class JetTrailDatabase : RoomDatabase() {
    abstract fun flightDao(): FlightDao
    abstract fun badgeDao(): BadgeDao

    companion object {
        @Volatile private var instance: JetTrailDatabase? = null

        fun get(context: Context): JetTrailDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                JetTrailDatabase::class.java,
                "jettrail.db",
            ).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build().also { instance = it }
        }
    }
}
