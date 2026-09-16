package com.example.iter.reservation.domain.entity

import com.example.iter.common.entity.BaseTimeEntity
import com.example.iter.reservation.api.RentalStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// ERD RENTAL 엔티티
// equipmentId/renterId는 각각 device/auth 도메인 PK를 값으로만 참조 (도메인 간 결합 최소화).
// *_snapshot 필드들은 예약 시점의 장비 정보를 그대로 복사해두는 값 — 이후 장비 정보가 바뀌어도 과거 예약 내역이 변하지 않도록 함.
//
// [주의] ERD.md의 RENTAL 테이블 정의에는 version 컬럼이 빠져 있지만,
// 기획서 5-1/8-1(Day5)에 명시된 "JPA 낙관적 락(@Version)" 동시성 제어를 구현하려면 반드시 필요한 컬럼이라 추가해두었다.
// -> ERD 원본(drawSQL) 업데이트가 필요하니 팀 공유 바랍니다.
//
// Lombok @Builder가 사라진 자리의 자바 호출부(RentalService 등 프로덕션, 여러 테스트)는
// 전부 생성자 위치 인자 호출로 고쳤다. 자바 선언 순서를 최대한 유지하되, 기본값이 있는
// categorySnapshot/rejectReason/approvedAt/status/version/id는 뒤로 옮겼다 — @JvmOverloads가
// 뒤쪽 기본값부터 생략한 오버로드를 만들어주려면 기본값 있는 파라미터가 끝에 몰려 있어야 한다
// (device Equipment.kt에서 id를 끝으로 옮긴 것과 같은 이유).
//
// status/rejectReason/approvedAt/version은 도메인 메서드(approve/reject/changeStatus 등)나
// Hibernate가 바꾸므로 본문 var 프로퍼티 + protected set으로 뒀다(주 생성자 프로퍼티는 접근자
// 수식어를 못 붙이고, plugin.jpa가 엔티티를 open으로 열어줘서 private set도 금지된다 — Equipment.kt
// 주석과 동일). id는 device 선례대로 val로 둬도 Hibernate가 리플렉션으로 생성값을 채워 넣는다.
@Entity
@Table(
    name = "rental",
    indexes = [
        Index(
            name = "idx_rental_renter_created_id",
            columnList = "renter_id, created_at DESC, id DESC",
        ),
        Index(
            name = "idx_rental_equipment_period",
            columnList = "equipment_id, start_date, end_date, id, status",
        ),
        Index(
            name = "idx_rental_status_created",
            columnList = "status, created_at",
        ),
        Index(
            name = "idx_rental_owner_created_id",
            columnList = "owner_id_snapshot, created_at DESC, id DESC",
        ),
        Index(
            name = "idx_rental_owner_end_date_status",
            columnList = "owner_id_snapshot, end_date, status",
        ),
    ],
)
class Rental @JvmOverloads constructor(

    @Column(name = "equipment_id", nullable = false)
    val equipmentId: Long,

    @Column(name = "owner_id_snapshot", nullable = false)
    val ownerIdSnapshot: Long,

    @Column(name = "renter_id", nullable = false)
    val renterId: Long,

    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    val endDate: LocalDate,

    @Column(name = "product_name_snapshot", nullable = false, length = 100)
    val productNameSnapshot: String,

    @Column(name = "daily_price_snapshot", nullable = false)
    val dailyPriceSnapshot: BigDecimal,

    @Column(name = "rental_days", nullable = false)
    val rentalDays: Int,

    @Column(name = "total_price", nullable = false)
    val totalPrice: BigDecimal,

    @Column(name = "receiver_name", length = 20)
    val receiverName: String? = null,

    @Column(name = "receiver_phone", length = 20)
    val receiverPhone: String? = null,

    @Column(length = 10)
    val zipcode: String? = null,

    @Column(length = 200)
    val address: String? = null,

    @Column(name = "detail_address", length = 200)
    val detailAddress: String? = null,

    @Column(name = "request_message", columnDefinition = "TEXT")
    val requestMessage: String? = null,

    rejectReason: String? = null,

    approvedAt: LocalDateTime? = null,

    status: RentalStatus = RentalStatus.PENDING,

    @Column(name = "category_snapshot", length = 50)
    val categorySnapshot: String? = null,

    @Version
    val version: Long? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseTimeEntity() {

    @Column(name = "reject_reason", length = 200)
    var rejectReason: String? = rejectReason
        protected set

    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = approvedAt
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RentalStatus = status
        protected set

    // ===== 도메인 메서드 =====

    fun isRenter(userId: Long?): Boolean = this.renterId == userId

    fun changeStatus(status: RentalStatus) {
        this.status = status
    }

    fun approve() {
        this.status = RentalStatus.APPROVED
        this.approvedAt = LocalDateTime.now()
    }

    fun reject(reason: String?) {
        this.status = RentalStatus.REJECTED
        this.rejectReason = reason
    }

    // 정상 반납으로 거래를 완료합니다.
    fun completeReturn() {
        this.status = RentalStatus.COMPLETED
    }

    // 비정상 반납으로 거래를 분쟁 상태로 변경합니다.
    fun openReturnDispute() {
        this.status = RentalStatus.DISPUTED
    }
}
