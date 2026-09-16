package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.SupplierDao
import com.example.posapp.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplierRepository @Inject constructor(
    private val supplierDao: SupplierDao
) {
    fun observeAll(): Flow<List<SupplierEntity>> = supplierDao.observeAll()

    suspend fun addSupplier(name: String, phone: String?, address: String?): Long =
        supplierDao.insert(
            SupplierEntity(
                name = name.trim(),
                phone = phone?.trim()?.takeIf { it.isNotBlank() },
                address = address?.trim()?.takeIf { it.isNotBlank() }
            )
        )

    suspend fun updateSupplier(supplier: SupplierEntity, name: String, phone: String?, address: String?) {
        supplierDao.update(
            supplier.copy(
                name = name.trim(),
                phone = phone?.trim()?.takeIf { it.isNotBlank() },
                address = address?.trim()?.takeIf { it.isNotBlank() }
            )
        )
    }

    /** Nonaktifkan pemasok tanpa menghapusnya — produk yang masih mereferensikan supplierId ini
     * tetap valid (supplierId sengaja tanpa FK constraint di DB, lihat ProductEntity.kt), hanya
     * tidak lagi muncul di daftar pemasok aktif untuk dipilih. */
    suspend fun setActive(supplier: SupplierEntity, active: Boolean) =
        supplierDao.update(supplier.copy(isActive = active))
}
