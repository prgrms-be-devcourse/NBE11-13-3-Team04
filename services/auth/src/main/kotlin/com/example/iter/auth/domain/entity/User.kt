package com.example.iter.auth.domain.entity

import com.example.iter.auth.api.PreferredLanguage
import com.example.iter.common.entity.BaseTimeEntity
import com.example.iter.common.security.AuthUser
import com.example.iter.common.security.Role
import com.example.iter.common.security.UserStatus
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
import java.time.LocalDateTime

// ERD USER 엔티티
// id, email(UK), password, name, nickname, phone, role, status, created_at, updated_at
@Entity
@Table(
    name = "users", // "user"는 MySQL 예약어와 충돌 위험이 있어 users로 지정
    indexes = [
        Index(name = "idx_users_created_id", columnList = "created_at DESC, id DESC"),
        Index(name = "idx_users_status_created_id", columnList = "status, created_at DESC, id DESC"),
    ],
)
class User @JvmOverloads constructor(
    email: String,
    password: String?,
    name: String,
    nickname: String?,
    phone: String?,
    role: Role = Role.USER,
    status: UserStatus = UserStatus.ACTIVE,
    pointBalance: BigDecimal = BigDecimal.ZERO,
    preferredLanguage: PreferredLanguage = PreferredLanguage.KO,
    deletedAt: LocalDateTime? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : BaseTimeEntity() {

    @Column(nullable = false, unique = true, length = 100)
    var email: String = email
        protected set

    @Column(length = 255)
    var password: String? = password
        protected set

    @Column(nullable = false, length = 20)
    var name: String = name
        protected set

    @Column(name = "nick_name", length = 20)
    var nickname: String? = nickname
        protected set

    @Column(length = 20)
    var phone: String? = phone
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: Role = role
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = status
        protected set

    // 실제 PG 미연동, 포인트 잔액을 깎는 방식의 mock 결제에 사용.
    // 가입 시 100만 포인트 자동 지급 로직은 A 담당 몫이라 여기서는 컬럼만 준비(기본값 0).
    // TODO: toss 결제 API 연결 대체
    @Column(name = "point_balance", nullable = false, precision = 12, scale = 0)
    var pointBalance: BigDecimal = pointBalance
        protected set

    // 이메일 등 백엔드가 직접 언어를 확정해서 보내야 하는 콘텐츠에만 쓰인다 (docs/i18n-frontend-handoff.md 참고).
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 10)
    var preferredLanguage: PreferredLanguage = preferredLanguage
        protected set

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = deletedAt
        protected set

    // ===== 도메인 메서드 =====

    fun changePassword(encodedPassword: String?) {
        this.password = encodedPassword
    }

    fun updateProfile(name: String, nickname: String?, phone: String?, preferredLanguage: PreferredLanguage) {
        this.name = name
        this.nickname = nickname
        this.phone = phone
        this.preferredLanguage = preferredLanguage
    }

    fun suspend() {
        status = UserStatus.SUSPENDED
    }

    fun restore() {
        status = UserStatus.ACTIVE
    }

    @JvmOverloads
    fun withdraw(deletedAt: LocalDateTime = LocalDateTime.now()) {
        status = UserStatus.DELETED
        this.deletedAt = deletedAt
    }

    fun isActive(): Boolean = status == UserStatus.ACTIVE

    // 시큐리티 계층에 넘길 스냅샷. AuthUser를 만드는 유일한 경로다.
    fun toAuthUser(): AuthUser = AuthUser.of(id!!, email, password, role, status)

    // Lombok @Builder를 코틀린 파일에 못 붙이는 문제 — 51+곳(테스트 다수 포함)이 쓰는
    // builder()...build() 체이닝을 그대로 유지하려고 직접 둔다. email/name처럼 non-null
    // 컬럼인데 호출부가 혹시 안 채워도 예외 대신 빈 문자열로 떨어지게 해서(자바 시절엔
    // null로 떨어지던 것과 동등한 "일단 컴파일·조립은 되고 DB 제약에서만 걸리는" 관용을 유지한다).
    class Builder {
        private var email: String? = null
        private var password: String? = null
        private var name: String? = null
        private var nickname: String? = null
        private var phone: String? = null
        private var role: Role = Role.USER
        private var status: UserStatus = UserStatus.ACTIVE
        private var pointBalance: BigDecimal = BigDecimal.ZERO
        private var preferredLanguage: PreferredLanguage = PreferredLanguage.KO
        private var deletedAt: LocalDateTime? = null
        private var id: Long? = null

        fun id(id: Long?) = apply { this.id = id }
        fun email(email: String) = apply { this.email = email }
        fun password(password: String?) = apply { this.password = password }
        fun name(name: String) = apply { this.name = name }
        fun nickname(nickname: String?) = apply { this.nickname = nickname }
        fun phone(phone: String?) = apply { this.phone = phone }
        fun role(role: Role) = apply { this.role = role }
        fun status(status: UserStatus) = apply { this.status = status }
        fun pointBalance(pointBalance: BigDecimal) = apply { this.pointBalance = pointBalance }

        fun preferredLanguage(preferredLanguage: PreferredLanguage) =
            apply { this.preferredLanguage = preferredLanguage }

        fun deletedAt(deletedAt: LocalDateTime?) = apply { this.deletedAt = deletedAt }

        fun build(): User = User(
            email = email ?: "",
            password = password,
            name = name ?: "",
            nickname = nickname,
            phone = phone,
            role = role,
            status = status,
            pointBalance = pointBalance,
            preferredLanguage = preferredLanguage,
            deletedAt = deletedAt,
            id = id,
        )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
