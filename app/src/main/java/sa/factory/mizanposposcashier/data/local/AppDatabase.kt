package sa.factory.mizanposposcashier.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MizanPosEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pos_cashierDao(): MizanPosDao
}
