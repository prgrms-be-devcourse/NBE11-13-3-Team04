-- docker-compose 로 MySQL 컨테이너를 처음 띄울 때 한 번만 실행된다
-- (볼륨이 비어 있을 때만 — 이미 데이터가 있으면 다시 안 돈다).
--
-- monolith 는 iter, chat 은 iter_chat 스키마를 쓴다. 같은 인스턴스지만
-- 크로스 스키마 조인은 금지한다 — 물리 분리 전 단계의 논리적 경계다.
CREATE DATABASE IF NOT EXISTS iter CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS iter_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
