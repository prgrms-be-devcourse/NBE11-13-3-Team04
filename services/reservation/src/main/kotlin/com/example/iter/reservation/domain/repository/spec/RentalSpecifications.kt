package com.example.iter.reservation.domain.repository.spec

import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental
import org.springframework.data.jpa.domain.Specification

// 대여자가 빌린 거래(findBorrowedHistory) 조회 조건을 조립한다.
// value/countQuery를 손으로 두 벌 유지하지 않기 위해 Specification으로 작성 (JpaSpecificationExecutor.findAll이
// 이 조건 하나로부터 목록 쿼리와 count 쿼리를 각각 생성해준다).
object RentalSpecifications {

    @JvmStatic
    fun renterIs(renterId: Long): Specification<Rental> =
        Specification { root, _, cb -> cb.equal(root.get<Long>("renterId"), renterId) }

    @JvmStatic
    fun hasStatus(status: RentalStatus?): Specification<Rental> =
        Specification { root, _, cb ->
            if (status == null) null else cb.equal(root.get<RentalStatus>("status"), status)
        }

    // 예약 당시 장비명(productNameSnapshot)에 검색어가 포함되는지(대소문자 무시) 확인한다.
    // LOCATE는 LIKE와 달리 검색어의 %, _를 와일드카드가 아닌 실제 문자로 취급하므로 그대로 사용한다
    // (LIKE로 바꾸면 검색어에 %/_가 포함될 때 의도와 다르게 동작한다).
    @JvmStatic
    fun equipmentNameContains(keyword: String?): Specification<Rental> =
        Specification { root, _, cb ->
            if (keyword == null) {
                null
            } else {
                val locatePosition = cb.function(
                    "locate", Int::class.javaObjectType,
                    cb.literal(keyword.lowercase()),
                    cb.lower(root.get<String>("productNameSnapshot")),
                )
                cb.gt(locatePosition, 0)
            }
        }

    @JvmStatic
    fun borrowedHistory(renterId: Long, status: RentalStatus?, equipmentName: String?): Specification<Rental> =
        Specification.where(renterIs(renterId))
            .and(hasStatus(status))
            .and(equipmentNameContains(equipmentName))
}
