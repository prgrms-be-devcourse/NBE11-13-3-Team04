# 시스템 스모크 테스트

모놀리스, 채팅, AI Fake provider, MySQL, Redis와 Toss 스텁을 별도 프로세스로 실행해 서비스 경계를 검증한다.

## 실행

저장소 루트에서 Python 개발 의존성을 설치한 뒤 실행한다.

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
cd ..
.\ai-service\.venv\Scripts\python.exe integration-tests\system\run.py
```

`run.py`는 다음 작업을 순서대로 수행하며 성공하거나 실패하면 Compose 리소스를 정리한다.

1. 모놀리스와 채팅 실행 JAR 생성
2. 격리된 Docker Compose 스택 실행
3. 결제 확정 이벤트가 Redis Stream을 지나 채팅방을 `TRADE`로 바꾸고 ACK되는지 검증
4. 모놀리스가 Redis에 발급한 채팅 ticket/grant로 채팅 서비스가 문의방을 생성하는지 검증
5. 반납 분쟁 신고가 실제 AI 서비스에서 처리되고 분석 작업에 연결되는지 검증
6. Spring `AiServiceClient`가 Python AI Fake 서비스에 세 기능을 접수하고 결과를 조회하는지 검증
7. 컨테이너와 임시 볼륨 제거

## 로컬 포트

| 서비스 | 포트 |
| --- | --- |
| MySQL | `13306` |
| Redis | `16379` |
| AI | `18000` |
| 모놀리스 | `18080` |
| 채팅 | `18081` |
| Toss 스텁 | `18090` |

상세 도메인 규칙은 앱별 통합 테스트에서 검증한다. 이 테스트는 서비스 사이의 연결과 공용 계약만 확인한다.
