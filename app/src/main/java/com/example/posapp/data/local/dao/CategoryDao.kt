package com.example.posapp.data.local.dao

import androidx.room.*
import com.example.posapp.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** Pencarian case-insensitive persis (bukan LIKE) — dipakai ProductImportUseCase untuk
     * mencocokkan/membuat kategori dari nama teks bebas di file CSV yang diimpor. */
    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}
