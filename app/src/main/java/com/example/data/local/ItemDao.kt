package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.StoredItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM stored_items ORDER BY expiryDate ASC")
    fun getAllItems(): Flow<List<StoredItem>>

    @Query("SELECT * FROM stored_items WHERE id = :id")
    suspend fun getItemById(id: Long): StoredItem?

    @Query("SELECT * FROM stored_items WHERE category = :category ORDER BY expiryDate ASC")
    fun getItemsByCategory(category: String): Flow<List<StoredItem>>

    @Query("SELECT * FROM stored_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): StoredItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: StoredItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<StoredItem>)

    @Update
    suspend fun updateItem(item: StoredItem)

    @Delete
    suspend fun deleteItem(item: StoredItem)

    @Query("DELETE FROM stored_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM stored_items")
    suspend fun deleteAllItems()

    @Query("SELECT * FROM stored_items")
    suspend fun getAllItemsSnapshot(): List<StoredItem>
}
