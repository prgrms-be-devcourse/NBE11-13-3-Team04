package iter.device.domain.repository

import iter.device.domain.entity.Equipment
import iter.device.domain.entity.EquipmentCategory
import iter.device.domain.entity.EquipmentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

interface EquipmentRepository : JpaRepository<Equipment, Long> {

    fun findByIdAndStatus(id: Long, status: EquipmentStatus): Optional<Equipment>

    // 기간 필터는 device 소유 가용성 프로젝션(EquipmentOccupancy)만 본다 — reservation의
    // Rental을 더 이상 직접 조인하지 않는다. 최종 일관성이지만, 실제 예약 생성 시점의
    // 비관적 락 재검증이 오버부킹을 막으므로 안전하다(자세한 근거는 EquipmentOccupancy 참고).
    //
    // escape 문자는 백슬래시 한 개다. 자바 텍스트 블록에서는 '\\' 로 적혀 있었는데, 그건
    // 자바가 이스케이프를 처리해 JPQL 에 한 개를 넘기기 때문이다. 코틀린 raw string 은
    // 이스케이프를 처리하지 않으므로 그대로 옮기면 두 개가 가서 escape 문자가 달라진다.
    // EquipmentQueryService.escapeLikePattern() 과 짝이다 — 한쪽만 틀리면 검색어의
    // % 와 _ 가 다시 와일드카드가 된다(% 를 넣으면 전체 조회가 되는 버그).
    @Query(
        value = """
            select e
            from Equipment e
            where e.status = EquipmentStatus.ACTIVE
              and (:keyword is null or lower(e.name) like lower(concat('%', :keyword, '%')) escape '\')
              and (:category is null or e.category = :category)
              and (:minPrice is null or e.dailyPrice >= :minPrice)
              and (:maxPrice is null or e.dailyPrice <= :maxPrice)
              and (
                  :startDate is null
                  or (
                      e.availableFrom <= :startDate
                      and e.availableTo >= :endDate
                      and not exists (
                          select occupancy.id
                          from EquipmentOccupancy occupancy
                          where occupancy.equipmentId = e.id
                            and occupancy.startDate <= :endDate
                            and occupancy.endDate >= :startDate
                      )
                  )
              )
            """,
        countQuery = """
            select count(e.id)
            from Equipment e
            where e.status = EquipmentStatus.ACTIVE
              and (:keyword is null or lower(e.name) like lower(concat('%', :keyword, '%')) escape '\')
              and (:category is null or e.category = :category)
              and (:minPrice is null or e.dailyPrice >= :minPrice)
              and (:maxPrice is null or e.dailyPrice <= :maxPrice)
              and (
                  :startDate is null
                  or (
                      e.availableFrom <= :startDate
                      and e.availableTo >= :endDate
                      and not exists (
                          select occupancy.id
                          from EquipmentOccupancy occupancy
                          where occupancy.equipmentId = e.id
                            and occupancy.startDate <= :endDate
                            and occupancy.endDate >= :startDate
                      )
                  )
              )
            """,
    )
    fun searchPublicEquipment(
        @Param("keyword") keyword: String?,
        @Param("category") category: EquipmentCategory?,
        @Param("minPrice") minPrice: BigDecimal?,
        @Param("maxPrice") maxPrice: BigDecimal?,
        @Param("startDate") startDate: LocalDate?,
        @Param("endDate") endDate: LocalDate?,
        pageable: Pageable,
    ): Page<Equipment>

    fun findByOwnerId(ownerId: Long, pageable: Pageable): Page<Equipment>

    fun findByOwnerIdAndStatus(
        ownerId: Long,
        status: EquipmentStatus,
        pageable: Pageable,
    ): Page<Equipment>

    /**
     * 대여 생성·승인 시 SELECT ... FOR UPDATE로 같은 장비에 대한 요청을 직렬화합니다.
     * 트랜잭션 안에서 장비 상태와 기간 충돌을 재검증한 뒤 상태 변경을 수행합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Equipment e WHERE e.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<Equipment>

    // 관리자가 장비명, 카테고리, 상태 조건으로 전체 장비를 조회합니다.
    // LOCATE를 사용해 %, _ 등의 문자를 와일드카드가 아닌 실제 검색 문자로 처리합니다.
    // 전달되지 않은 조건은 조회에 적용하지 않습니다.
    @Query(
        """
        select e
        from Equipment e
        where (
                :keyword is null
                or locate(lower(:keyword), lower(e.name)) > 0
              )
          and (
                :category is null
                or lower(e.category) = lower(:category)
              )
          and (
                :status is null
                or e.status = :status
              )
          and (
                :cursorCreatedAt is null
                or e.createdAt < :cursorCreatedAt
                or (e.createdAt = :cursorCreatedAt and e.id < :cursorId)
              )
        order by e.createdAt desc, e.id desc
        """
    )
    fun searchForAdminByCursor(
        @Param("keyword") keyword: String?,
        @Param("category") category: String?,
        @Param("status") status: EquipmentStatus?,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorId") cursorId: Long?,
        pageable: Pageable,
    ): List<Equipment>

    // flushAutomatically 만 두고 clearAutomatically 는 붙이지 않는다 — 붙이면 호출자가
    // 잡고 있던 User 락이 풀린다(포트 javadoc 에 근거가 있다).
    @Modifying(flushAutomatically = true)
    @Query(
        """
        update Equipment e
        set e.status = :status, e.updatedAt = :updatedAt
        where e.ownerId = :ownerId and e.status <> :status
        """
    )
    fun updateStatusByOwnerId(
        @Param("ownerId") ownerId: Long,
        @Param("status") status: EquipmentStatus,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int
}
