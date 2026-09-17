package com.example.iter.dispute.domain.entity

import com.example.iter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

// REPORT 엔티티 — 대여 건과 무관하게 회원/장비 자체를 신고하는 "일반 신고" (rental 단위 분쟁인 DISPUTE와는 구분됨)
// reporterId/targetId는 auth(User) 또는 device(Equipment) 도메인 PK를 값으로만 참조 (도메인 간 결합 최소화)
//
// [주의] 구체적인 필드 목록은 ERD 변경사항에 target_type/status enum만 명시되어 있어
// DISPUTE 엔티티와 동일한 형태로 유추해 작성했습니다. 실제 컬럼 구성은 팀 확인 후 조정해주세요.
@Entity
@Table(
    name = "report",
    indexes = [
        Index(name = "idx_report_created_id", columnList = "created_at DESC, id DESC"),
        Index(name = "idx_report_status_target_created_id", columnList = "status, target_type, created_at DESC, id DESC"),
        Index(name = "idx_report_reporter_created_id", columnList = "reporter_id, created_at DESC, id DESC"),
        Index(name = "idx_report_active_target", columnList = "reporter_id, target_type, target_id, status"),
    ],
)
class Report @JvmOverloads constructor(
    @Column(name = "reporter_id", nullable = false)
    val reporterId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    val targetType: ReportTargetType,

    @Column(name = "target_id", nullable = false)
    val targetId: Long,

    @Column(nullable = false, length = 50)
    val reason: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    status: ReportStatus = ReportStatus.RECEIVED,

    adminMemo: String? = null,

    resolvedAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : BaseTimeEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReportStatus = status
        protected set

    @Column(name = "admin_memo", columnDefinition = "TEXT")
    var adminMemo: String? = adminMemo
        protected set

    @Column(name = "resolved_at")
    var resolvedAt: LocalDateTime? = resolvedAt
        protected set

    // 관리자가 신고 상태와 처리 메모를 변경합니다. 해결 또는 기각 상태에서는 처리 완료 시각을 저장합니다.
    fun changeStatusByAdmin(status: ReportStatus, adminMemo: String?, processedAt: LocalDateTime?) {
        this.status = status
        this.adminMemo = adminMemo

        if (status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED) {
            this.resolvedAt = processedAt
            return
        }

        this.resolvedAt = null
    }
}
