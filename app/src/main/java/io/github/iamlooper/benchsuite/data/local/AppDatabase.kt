package io.github.iamlooper.benchsuite.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocalRunEntity::class,
        LocalResultEntity::class,
        LocalCategoryScoreEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun resultDao(): ResultDao
}
