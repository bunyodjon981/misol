package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApkDao {
    @Query("SELECT * FROM apk_records ORDER BY createdAtMillis DESC")
    fun getAllApks(): Flow<List<ApkRecordEntity>>

    @Query("SELECT * FROM apk_records WHERE id = :id LIMIT 1")
    suspend fun getApkById(id: Long): ApkRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApk(apk: ApkRecordEntity): Long

    @Query("DELETE FROM apk_records WHERE id = :id")
    suspend fun deleteApkById(id: Long)
}
