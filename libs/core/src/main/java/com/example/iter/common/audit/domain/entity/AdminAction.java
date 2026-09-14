package com.example.iter.common.audit.domain.entity;

import com.example.iter.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.*;

// ERD ADMIN_ACTION — 관리자 조치 감사 로그.
// adminId/targetId는 다른 도메인(User/Equipment/Dispute)의 PK를 값으로만 들고,
// JPA 연관관계(FK 매핑)는 의도적으로 걸지 않는다 — FK를 걸면 admin_action이 참조 대상
// 도메인(User/Equipment/Dispute)의 스키마·생명주기에 묶여서, 그 도메인 쪽 테이블이
// 바뀌거나 분리될 때마다 감사 로그까지 함께 손봐야 한다.
@Entity
@Table(
        name = "admin_action",
        indexes = {
                @Index(
                        name = "idx_admin_action_created_id",
                        columnList = "created_at DESC, id DESC"
                ),
                @Index(
                        name = "idx_admin_action_target_created_id",
                        columnList = "target_type, target_id, action, created_at DESC, id DESC"
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AdminAction extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AdminActionTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminActionType action;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
