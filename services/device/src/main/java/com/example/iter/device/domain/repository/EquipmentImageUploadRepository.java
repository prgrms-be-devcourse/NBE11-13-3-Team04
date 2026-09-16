package com.example.iter.device.domain.repository;

import com.example.iter.device.domain.entity.EquipmentImageUpload;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EquipmentImageUploadRepository
        extends JpaRepository<EquipmentImageUpload, Long> {

    List<EquipmentImageUpload> findAllByObjectKeyIn(Collection<String> objectKeys);

    // 장비 등록 중 같은 임시 이미지를 동시에 사용하지 못하도록 대상 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from EquipmentImageUpload u where u.objectKey in :objectKeys order by u.id")
    List<EquipmentImageUpload> findAllByObjectKeyInForUpdate(
            @Param("objectKeys") Collection<String> objectKeys
    );
}
