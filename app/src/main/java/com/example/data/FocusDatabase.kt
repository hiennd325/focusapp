package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FocusSession::class, FocusLockLog::class], version = 1, exportSchema = false)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao

    companion object {
        @Volatile
        private var INSTANCE: FocusDatabase? = null

        fun getDatabase(context: Context): FocusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusDatabase::class.java,
                    "focuslock_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
