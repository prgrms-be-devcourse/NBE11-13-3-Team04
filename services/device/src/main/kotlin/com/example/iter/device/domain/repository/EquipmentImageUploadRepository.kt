package com.example.iter.device.domain.repository

import com.example.iter.device.domain.entity.EquipmentImageUpload
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EquipmentImageUploadRepository : JpaRepository<EquipmentImageUpload, Long> {

    // order by u.id 는 데드락 회피용 락 획득 순서다. 빼지 말 것.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from EquipmentImageUpload u where u.objectKey in :objectKeys order by u.id")
    fun findAllByObjectKeyInForUpdate(
        @Param("objectKeys") objectKeys: Collection<String>,
    ): List<EquipmentImageUpload>
}
