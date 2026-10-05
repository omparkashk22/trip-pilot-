package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.RideLog

@Database(entities = [RideLog::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideLogDao(): RideLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_logs_fingerprint` ON `ride_logs` (`fingerprint`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_logs_timestamp` ON `ride_logs` (`timestamp`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ride_logs` ADD COLUMN `firstSeenAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ride_logs` ADD COLUMN `lastSeenAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `ride_logs` SET `firstSeenAt` = `timestamp`, `lastSeenAt` = `timestamp` WHERE `firstSeenAt` = 0")
                db.execSQL("UPDATE `ride_logs` SET `seenCount` = 1 WHERE `seenCount` <= 0")
                db.execSQL("UPDATE `ride_logs` SET `rideType` = 'Unknown' WHERE `rideType` LIKE '%Notification%' OR length(`rideType`) > 24")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ride_logs` ADD COLUMN `eventLagMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ride_logs` ADD COLUMN `parseMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ride_logs` ADD COLUMN `decideMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ride_logs` ADD COLUMN `totalToTapMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ride_logs` ADD COLUMN `outcome` TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trip_pilot_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
