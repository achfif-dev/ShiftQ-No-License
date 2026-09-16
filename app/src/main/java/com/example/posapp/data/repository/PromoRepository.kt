package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.PromoDao
import com.example.posapp.data.local.entity.PromoEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromoRepository @Inject constructor(
    private val promoDao: PromoDao
) {
    fun observeAll(): Flow<List<PromoEntity>> = promoDao.observeAll()

    suspend fun save(promo: PromoEntity) {
        if (promo.id == 0L) promoDao.insert(promo) else promoDao.update(promo)
    }

    suspend fun setActive(promo: PromoEntity, active: Boolean) {
        promoDao.update(promo.copy(isActive = active))
    }

    suspend fun delete(id: Long) = promoDao.delete(id)
}
