package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.AuditLogDao
import com.example.posapp.data.local.entity.AuditLogEntity
import com.example.posapp.data.local.entity.UserRole
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepository @Inject constructor(
    private val auditLogDao: AuditLogDao
) {
    fun observeRecent(): Flow<List<AuditLogEntity>> = auditLogDao.observeRecent()

    suspend fun log(actorName: String, actorRole: UserRole?, action: String, description: String) {
        auditLogDao.insert(
            AuditLogEntity(
                actorName = actorName,
                actorRole = actorRole?.name ?: "-",
                action = action,
                description = description
            )
        )
    }
}
