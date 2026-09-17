-- 2026-08-24 "알림 API 응답을 완성 문장 대신 type + params로 전환"(2607817) 이후 Notification
-- 엔티티는 title/message 를 더 이상 저장하지 않는다. 그런데 그 이전에 ddl-auto: update 로 만들어진
-- DB 에는 두 컬럼이 NOT NULL · 기본값 없음 상태로 그대로 남아 있다.
-- 그 결과 sql_mode 의 STRICT_TRANS_TABLES 아래에서 이후의 모든 알림 INSERT 가
--   Field 'title' doesn't have a default value
-- 로 실패했고, NotificationEventListener.notify() 가 그 예외를 잡아 로그만 남기기 때문에
-- 대여/결제 API 는 정상 200 인데 알림만 조용히 사라졌다.
--
-- Hibernate 의 ddl-auto: validate 는 "엔티티가 매핑한 컬럼이 있는지"만 보고 테이블에 남은
-- 잉여 컬럼은 문제 삼지 않아서 기동 시점에도 걸리지 않았다.
-- V1__init_schema.sql 에는 이미 두 컬럼이 없지만, 이 DB 는 flyway baseline-on-migrate 로
-- V1 이 "적용된 것으로 치고" 넘어갔기 때문에 실제로 반영된 적이 없다.
--
-- V1 로 새로 만든 DB 에는 두 컬럼이 애초에 없으므로, 존재할 때만 지우도록 조건부로 실행한다
-- (MySQL 8 은 ALTER TABLE ... DROP COLUMN IF EXISTS 를 지원하지 않는다).
set @drop_title := (
    select if(count(*) > 0, 'alter table notification drop column title', 'do 0')
    from information_schema.columns
    where table_schema = database() and table_name = 'notification' and column_name = 'title'
);
prepare stmt from @drop_title;
execute stmt;
deallocate prepare stmt;

set @drop_message := (
    select if(count(*) > 0, 'alter table notification drop column message', 'do 0')
    from information_schema.columns
    where table_schema = database() and table_name = 'notification' and column_name = 'message'
);
prepare stmt from @drop_message;
execute stmt;
deallocate prepare stmt;

-- 같은 전환 때 추가된 params 컬럼은 기존 행에 빈 문자열로 채워졌다. 빈 문자열은 JSON 이 아니라서
-- NotificationParamsConverter 가 역직렬화에 실패하고, 그 행이 한 건이라도 걸리면
-- GET /api/v1/notifications 응답 전체가 500 이 된다. 빈 객체로 채워 읽을 수 있게 만든다.
-- (그 시절 알림의 보간 값은 title/message 안에만 있었고 구조화된 형태로는 남아있지 않다 —
--  프론트는 params 가 비면 해당 자리를 빈 문자열로 렌더링한다.)
update notification set params = '{}' where params = '' or params is null;
