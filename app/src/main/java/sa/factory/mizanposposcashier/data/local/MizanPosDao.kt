package sa.factory.mizanposposcashier.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MizanPosDao {
    @Query("SELECT * FROM pos_cashier_items_cache ORDER BY id ASC")
    fun observeAllItems(): Flow<List<MizanPosEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MizanPosEntity>)

    @Query("DELETE FROM pos_cashier_items_cache")
    suspend fun clearAll()
}
