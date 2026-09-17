-- chat 서비스 초기 스키마. iter_chat 스키마 전용이며 monolith의 iter 스키마를 조인하지
-- 않는다(FK도 걸지 않는다) — 물리 분리 전 단계의 논리적 경계다.
CREATE TABLE chat_rooms
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id   BIGINT                              NOT NULL,
    equipment_name VARCHAR(255)                        NOT NULL,
    owner_id       BIGINT                              NOT NULL,
    requester_id   BIGINT                              NOT NULL,
    rental_id      BIGINT                              NULL,
    stage          ENUM ('INQUIRY', 'TRADE', 'CLOSED') NOT NULL DEFAULT 'INQUIRY',
    created_at     DATETIME(6)                         NOT NULL,
    CONSTRAINT uk_equipment_requester UNIQUE (equipment_id, requester_id)
) ENGINE = InnoDB;

-- Spring Data R2DBC는 복합 @Id를 지원하지 않아서, 관계상 (room_id, user_id)가
-- 자연키인 테이블에도 대리키(id)를 둔다. 중복 방지는 UNIQUE 제약이 한다.
CREATE TABLE room_participants
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id              BIGINT       NOT NULL,
    user_id              BIGINT       NOT NULL,
    nickname             VARCHAR(100) NOT NULL,
    last_read_message_id BIGINT       NULL,
    joined_at            DATETIME(6)  NOT NULL,
    -- 문의 단계 위반 3회 누적 시 이 방에서 이 시각까지 전송을 막는다.
    muted_until          DATETIME(6)  NULL,
    CONSTRAINT uk_room_user UNIQUE (room_id, user_id)
) ENGINE = InnoDB;

CREATE TABLE messages
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id          BIGINT               NOT NULL,
    sender_id        BIGINT               NULL,
    sender_nickname  VARCHAR(100)         NULL,
    type             ENUM ('USER', 'SYSTEM') NOT NULL,
    -- 마스킹된 본문만 저장한다. 원문은 어디에도 남기지 않는다(개인정보 차단 취지와
    -- 원문 보관이 같이 갈 수 없어서 — .docs/05-chat-and-realtime.md 참고).
    content          VARCHAR(1000)        NOT NULL,
    masked           BOOLEAN              NOT NULL DEFAULT FALSE,
    sent_at          DATETIME(6)          NOT NULL,
    KEY idx_room_id_id (room_id, id DESC)
) ENGINE = InnoDB;

CREATE TABLE chat_violations
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id      BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    message_id   BIGINT       NOT NULL,
    category     VARCHAR(30)  NOT NULL,
    occurred_at  DATETIME(6)  NOT NULL,
    KEY idx_user_occurred (user_id, occurred_at)
) ENGINE = InnoDB;
