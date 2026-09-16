package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.CategoryDao
import com.example.posapp.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun observeAll(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun upsert(category: CategoryEntity): Long =
        if (category.id == 0L) categoryDao.insert(category) else {
            categoryDao.update(category); category.id
        }

    suspend fun delete(category: CategoryEntity) = categoryDao.delete(category)
}
