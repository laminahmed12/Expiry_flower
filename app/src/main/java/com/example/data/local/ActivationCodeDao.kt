package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ActivationCode
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivationCodeDao {
    @Query("SELECT * FROM activation_codes ORDER BY createdAt DESC")
    fun getAllCodes(): Flow<List<ActivationCode>>

    @Query("SELECT * FROM activation_codes ORDER BY createdAt DESC")
    suspend fun getAllCodesSnapshot(): List<ActivationCode>

    @Query("SELECT * FROM activation_codes WHERE code = :code LIMIT 1")
    suspend fun getCode(code: String): ActivationCode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCode(code: ActivationCode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(codes: List<ActivationCode>)

    @Update
    suspend fun updateCode(code: ActivationCode)

    @Query("UPDATE activation_codes SET isRevoked = 1 WHERE code = :code")
    suspend fun revokeCode(code: String)

    @Query("UPDATE activation_codes SET isRevoked = 0 WHERE code = :code")
    suspend fun activateCode(code: String)

    @Query("DELETE FROM activation_codes WHERE code = :code")
    suspend fun deleteCode(code: String)
}
