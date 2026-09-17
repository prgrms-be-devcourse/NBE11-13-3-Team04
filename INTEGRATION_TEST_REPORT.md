## 1. 결론

현재 자동화된 통합·계약·시스템 테스트는 모두 통과했다. 앱 내부 흐름은 실제 MySQL/Redis를 사용하고, 서비스 간 핵심 연결은 Docker Compose로 각 서비스를 띄워 검증했다.

## 2. 실행 결과

| 구분 | 실행 위치 | 통과 / 실행 | 실패 | 건너뜀 |
| --- | --- | ---: | ---: | ---: |
| 모놀리스 통합 | `:apps:monolith:integrationTest` | 31 / 31 | 0 | 0 |
| 채팅 통합 | `:apps:chat:integrationTest` | 10 / 10 | 0 | 0 |
| 서비스 간 Docker 스모크 | `integration-tests/system/test_system_smoke.py` | 3 / 3 | 0 | 0 |
| Spring ↔ Python AI 시스템 | `:services:ai:systemTest` | 1 / 1 | 0 | 0 |
| Spring AI 계약 | `:services:ai:contractTest` | 3 / 3 | 0 | 0 |
| Python AI 통합 | `ai-service/tests/integration` | 4 / 4 | 0 | 0 |
| Python AI 계약 | `ai-service/tests/contract` | 9 / 9 | 0 | 0 |


### 이번 검증에서 실제로 실행한 명령

```powershell
.\gradlew.bat :apps:chat:integrationTest :apps:monolith:integrationTest --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :services:ai:contractTest --rerun-tasks --no-daemon --console=plain
.\ai-service\.venv\Scripts\python.exe -m pytest ai-service/tests -q
.\ai-service\.venv\Scripts\python.exe integration-tests\system\run.py
```

`run.py`는 Docker Compose 스택을 새로 띄운 뒤 Python 스모크 3건과 Spring AI 시스템 테스트 1건을 실행하고 컨테이너·임시 볼륨을 정리한다. `systemTest`는 외부 AI 서비스의 새 실행 상태를 Gradle이 입력 파일만으로 판단할 수 없으므로 결과 캐시를 사용하지 않도록 설정했다. 이번 검증에서 해당 태스크가 실제 실행되어 통과한 것을 확인했다. 로컬 검증이므로 GitHub Actions의 원격 실행 결과를 의미하지는 않는다.

## 3. 테스트 코드 위치와 구성

| 위치 | 구성 |
| --- | --- |
| `apps/monolith/src/integrationTest/` | 모놀리스의 독립 `integrationTest` 소스셋 |
| `apps/chat/src/test/`의 `*IntegrationTest.kt` | `@Tag("integration")`으로 분리한 채팅 테스트 |
| `integration-tests/system/test_system_smoke.py` | Python 기반 서비스 간 시스템 스모크 테스트 |
| `services/ai/src/test/`의 `@Tag("system")`, `@Tag("contract")` | Spring AI 시스템·계약 테스트 |
| `ai-service/tests/integration/`, `ai-service/tests/contract/` | Python AI 통합·계약 테스트 |
| `integration-tests/contracts/` | Spring과 Python이 함께 사용하는 계약 fixture |

앱별 JVM 통합 테스트는 Testcontainers로 MySQL과 Redis를 실행한다. 서비스 간 시스템 테스트는 Docker Compose로 각 서비스를 별도 프로세스로 실행한다. 구체적인 검증 내용은 4번에 정리했다.

## 4. 어떤 테스트를 했는가

| 영역 | 실제로 확인한 내용 |
| --- | --- |
| 스키마·인증 | 빈 MySQL에 Flyway 마이그레이션을 적용하고 엔티티 스키마를 검증했다. 회원 로그인, 액세스 토큰 인증, 리프레시 토큰 갱신·로그아웃, 정지·탈퇴 계정의 접근 제한을 확인했다. |
| 장비·이미지 | 장비 등록, 이미지 업로드 권한 발급과 연결, 공개 조회·검색, 대여 중 상태 변경·삭제 제한을 확인했다. 업로드 기록과 S3 메타데이터 응답을 검사하며 실제 S3 객체 저장은 테스트 대역을 사용했다. |
| 대여·결제·배송 | 대여 생성부터 결제 준비·승인, 소유자 승인, 배송·수령·반납·후기까지 상태와 DB 반영을 확인했다. 결제 실패·재시도·웹훅 재전송, 취소·거절·환불, 기간이 겹치는 동시 대여와 상호 대여 경합도 확인했다. |
| 알림·신고·관리자 | 거래 이벤트가 커밋된 뒤 알림이 저장·전달되는지, 읽음 상태와 SSE 응답이 맞는지 확인했다. 반납 이상 신고의 분쟁 전환과 관리자 신고 조회·상태 변경·권한을 확인했다. |
| 채팅 | ticket/grant로 방을 만들고 REST·WebSocket으로 메시지를 주고받는지 확인했다. 재접속, 메시지 저장·읽음, 연락처 마스킹, 반복 위반 시 방 전송 제한, Redis 결제 이벤트 소비를 확인했다. |
| AI·계약 | Spring과 Python이 공유 JSON 형식을 수용하는지 확인했다. 작업 생성·조회, 중복 요청 처리, 처리 중 작업 복구를 확인했다. |
| 서비스 간 연결 | Docker에서 모놀리스·채팅·AI·MySQL·Redis·Toss 대역을 함께 실행했다. 결제 확정 이벤트가 채팅방 상태를 바꾸고 ACK되는지, 모놀리스가 발급한 채팅 권한으로 방이 생성되는지, 분쟁 신고가 AI 분석 작업과 연결되는지 확인했다. |


## 5. 다시 실행하는 방법

저장소 루트에서 JDK 25, Python 3.12 개발 의존성, 응답 가능한 Docker 엔진이 필요하다. Windows PowerShell 기준 명령은 다음과 같다.

```powershell
# 앱별 통합 테스트: MySQL/Redis Testcontainers 사용
.\gradlew.bat projectIntegrationTest

# Spring AI 계약과 Python AI 테스트
.\gradlew.bat contractTest
.\ai-service\.venv\Scripts\python.exe -m pytest ai-service/tests

# 여러 서비스를 함께 띄우는 시스템 테스트; 완료 후 Compose 자원 정리
.\ai-service\.venv\Scripts\python.exe integration-tests\system\run.py
```

첫 실행 전에 Python 가상환경과 의존성이 없다면 `ai-service/requirements-dev.txt`를 설치해야 한다. 앱별 테스트만 필요하면 `:apps:monolith:integrationTest` 또는 `:apps:chat:integrationTest`를 선택해 실행한다. `projectIntegrationTest`는 두 JVM 앱 테스트를 묶는 태스크이며, Docker 시스템 테스트와 Python 테스트까지 포함하는 단일 명령은 아니다. Linux CI에서는 `./gradlew`와 `python` 명령을 사용한다. CI 워크플로는 `.github/workflows/ci.yml`에 각 묶음을 별도 단계로 실행하도록 구성되어 있다.
