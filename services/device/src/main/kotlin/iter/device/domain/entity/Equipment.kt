package iter.device.domain.entity

import iter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

import java.math.BigDecimal
import java.time.LocalDate

// ERD EQUIPMENT 엔티티
// ownerId는 auth 도메인 User의 PK를 값으로만 참조한다 (도메인 간 JPA 연관관계를 걸지 않음 — AdminAction과 동일한 이유).
//
// 바뀌는 필드는 주 생성자 파라미터로만 받고 본문 프로퍼티로 선언한다. 자바에서 @Getter 만 있고
// @Setter 가 없던 구조 — 바깥에서는 못 바꾸고 도메인 메서드로만 바꾼다 — 를 유지하려면
// protected set 이 필요한데, 주 생성자 프로퍼티에는 접근자 수식어를 붙일 수 없기 때문이다.
// (private set 도 못 쓴다: plugin.jpa 가 @Entity 를 open 으로 열어주고, 코틀린은 open
//  프로퍼티의 private setter 를 금지한다.)
@Entity
@Table(
    name = "equipment",
    indexes = [
        Index(
            name = "idx_equipment_created_id",
            columnList = "created_at DESC, id DESC",
        ),
        Index(
            name = "idx_equipment_status_created_id",
            columnList = "status, created_at DESC, id DESC",
        ),
        Index(
            name = "idx_equipment_owner_created_id",
            columnList = "owner_id, created_at DESC, id DESC",
        ),
    ],
)
class Equipment @JvmOverloads constructor(

    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val category: EquipmentCategory,

    name: String,

    description: String?,

    dailyPrice: BigDecimal,

    availableFrom: LocalDate? = null,

    availableTo: LocalDate? = null,

    status: EquipmentStatus = EquipmentStatus.ACTIVE,

    productCondition: ProductConditionType = ProductConditionType.NORMAL,

    conditionDetail: String? = null,

    // 자바 필드 선언에서는 맨 앞이었지만 생성자에서는 맨 뒤에 둔다 — @JvmOverloads 가
    // 뒤쪽 기본값부터 생략한 오버로드를 만들어주므로, 자바 호출부가 id 자리에 null 을
    // 넘기지 않아도 된다. 나머지 필드 순서는 자바 선언 그대로다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseTimeEntity() {

    @Column(nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Column(name = "daily_price", nullable = false, precision = 10, scale = 0)
    var dailyPrice: BigDecimal = dailyPrice
        protected set

    @Column(name = "available_from")
    var availableFrom: LocalDate? = availableFrom
        protected set

    @Column(name = "available_to")
    var availableTo: LocalDate? = availableTo
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: EquipmentStatus = status
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "product_condition", nullable = false, length = 20)
    var productCondition: ProductConditionType = productCondition
        protected set

    @Column(name = "condition_detail", columnDefinition = "TEXT")
    var conditionDetail: String? = conditionDetail
        protected set

    // ===== 도메인 메서드 =====

    // userId 를 Long? 으로 받는다. non-null Long 으로 두면 JVM 시그니처가 isOwnedBy(long) 이
    // 되어 자바가 넘긴 Long 이 null 일 때 언박싱 NPE 가 난다 — 기존 ownerId.equals(userId) 는
    // null 에 false 를 돌려줬다.
    fun isOwnedBy(userId: Long?): Boolean = ownerId == userId

    fun isActive(): Boolean = status == EquipmentStatus.ACTIVE

    fun changeStatus(status: EquipmentStatus) {
        this.status = status
    }

    fun update(
        name: String,
        description: String?,
        dailyPrice: BigDecimal,
        availableFrom: LocalDate?,
        availableTo: LocalDate?,
        productCondition: ProductConditionType,
        conditionDetail: String?,
    ) {
        this.name = name
        this.description = description
        this.dailyPrice = dailyPrice
        this.availableFrom = availableFrom
        this.availableTo = availableTo
        this.productCondition = productCondition
        this.conditionDetail = conditionDetail
    }

    fun delete() {
        this.status = EquipmentStatus.DELETED
    }
}
