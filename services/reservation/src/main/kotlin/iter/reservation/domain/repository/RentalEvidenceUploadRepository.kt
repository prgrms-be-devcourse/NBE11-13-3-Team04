package iter.reservation.domain.repository

import iter.reservation.domain.entity.EvidenceUploadPhase
import iter.reservation.domain.entity.RentalEvidenceUpload
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RentalEvidenceUploadRepository : JpaRepository<RentalEvidenceUpload, Long> {
    fun countByRentalIdAndUserIdAndPhase(rentalId: Long, userId: Long, phase: EvidenceUploadPhase): Long

    // 같은 증빙 키가 동시에 여러 요청에 사용되지 않도록 조회 시 행을 잠급니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from RentalEvidenceUpload u where u.objectKey in :objectKeys order by u.id")
    fun findAllByObjectKeyInForUpdate(@Param("objectKeys") objectKeys: Collection<String>): List<RentalEvidenceUpload>
}
