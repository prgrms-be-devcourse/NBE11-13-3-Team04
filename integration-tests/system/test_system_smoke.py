import base64
import hashlib
import hmac
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from datetime import date, timedelta
from pathlib import Path
from typing import Any

import pymysql
import pytest


MONOLITH_URL = os.getenv("SYSTEM_MONOLITH_URL", "http://127.0.0.1:18080")
CHAT_URL = os.getenv("SYSTEM_CHAT_URL", "http://127.0.0.1:18081")
AI_URL = os.getenv("SYSTEM_AI_URL", "http://127.0.0.1:18000")

# compose.yml의 monolith 서비스에 심어 둔 JWT_SECRET_KEY와 같은 값이다.
# jwt.issuer=iter, jwt.audience=iter-api는 apps/monolith/src/main/resources/application.yaml의 고정값이다.
JWT_SECRET_KEY_B64 = (
    "aW50ZWdyYXRpb24tc3lzdGVtLXRlc3Qtand0LXNlY3JldC1rZXktbXVzdC1iZS1sb25nLWVub3VnaC1mb3ItaHM1MTItc2lnbmluZw=="
)
JWT_ISSUER = "iter"
JWT_AUDIENCE = "iter-api"


@pytest.fixture(scope="session", autouse=True)
def wait_for_system_services() -> None:
    # 준비 확인은 서비스 묶음당 한 번만 한다. 기동 실패 시 각 테스트가 120초씩 같은
    # 실패를 반복하지 않고 한 번의 명확한 진단으로 종료된다.
    wait_for_services()


def test_payment_event_moves_chat_room_to_trade_and_is_acked() -> None:
    owner_email = f"system-owner-{time.time_ns()}@integration.test"
    renter_email = f"system-renter-{time.time_ns()}@integration.test"
    password = "Password123!"

    signup(owner_email, password, "소유자", "owner")
    signup(renter_email, password, "대여자", "renter")
    renter_token = login(renter_email, password)

    with mysql_connection("iter") as connection:
        owner_id = user_id(connection, owner_email)
        renter_id = user_id(connection, renter_email)
        start_date = date.today() + timedelta(days=3)
        end_date = start_date + timedelta(days=2)
        with connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO equipment (
                    available_from, available_to, daily_price, created_at, updated_at,
                    owner_id, name, description, category, product_condition, status
                ) VALUES (%s, %s, %s, NOW(6), NOW(6), %s, %s, %s, %s, %s, %s)
                """,
                (
                    date.today() + timedelta(days=1),
                    date.today() + timedelta(days=30),
                    50000,
                    owner_id,
                    "시스템 테스트 카메라",
                    "모놀리스와 채팅 연결 검증",
                    "CAMERA",
                    "NORMAL",
                    "ACTIVE",
                ),
            )
            equipment_id = cursor.lastrowid

    with mysql_connection("iter_chat") as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO chat_rooms (
                    equipment_id, equipment_name, owner_id, requester_id,
                    rental_id, stage, created_at
                ) VALUES (%s, %s, %s, %s, NULL, 'INQUIRY', NOW(6))
                """,
                (equipment_id, "시스템 테스트 카메라", owner_id, renter_id),
            )
            room_id = cursor.lastrowid

    created = request_json(
        "POST",
        f"{MONOLITH_URL}/api/v1/rentals",
        {
            "equipmentId": equipment_id,
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
            "receiverName": "시스템 테스트 대여자",
            "receiverPhone": "010-1234-5678",
            "zipcode": "06236",
            "address": "서울시 강남구 테헤란로",
            "detailAddress": "시스템 테스트 101호",
            "requestMessage": "도착 전 연락해주세요.",
            "useDefaultAddress": True,
        },
        renter_token,
    )
    assert created[0] == 201, created
    rental_id = created[1]["rentalId"]

    ready = request_json(
        "POST",
        f"{MONOLITH_URL}/api/v1/rentals/{rental_id}/payment/ready",
        {},
        renter_token,
    )
    assert ready[0] == 200, ready

    confirmed = request_json(
        "POST",
        f"{MONOLITH_URL}/api/v1/rentals/{rental_id}/payment/confirm",
        {
            "paymentKey": "system-payment-key",
            "orderId": ready[1]["orderId"],
            "amount": ready[1]["amount"],
        },
        renter_token,
    )
    assert confirmed[0] == 200, confirmed

    deadline = time.monotonic() + 20
    room = None
    while time.monotonic() < deadline:
        with mysql_connection("iter_chat") as connection:
            with connection.cursor(pymysql.cursors.DictCursor) as cursor:
                cursor.execute("SELECT stage, rental_id FROM chat_rooms WHERE id = %s", room_id)
                room = cursor.fetchone()
        if room == {"stage": "TRADE", "rental_id": rental_id}:
            break
        time.sleep(0.2)

    assert room == {"stage": "TRADE", "rental_id": rental_id}
    assert stream_pending_count() == 0


def test_monolith_ticket_and_grant_create_chat_room_over_shared_redis() -> None:
    """IT-CHAT-013: 발급과 소비를 실제 서비스 HTTP 경계에서 검증한다."""
    owner_email = f"system-chat-owner-{time.time_ns()}@integration.test"
    requester_email = f"system-chat-requester-{time.time_ns()}@integration.test"
    password = "Password123!"

    signup(owner_email, password, "채팅 등록자", "chat-owner")
    signup(requester_email, password, "채팅 요청자", "chat-requester")
    requester_token = login(requester_email, password)

    with mysql_connection("iter") as connection:
        owner_id = user_id(connection, owner_email)
        requester_id = user_id(connection, requester_email)
        with connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO equipment (
                    available_from, available_to, daily_price, created_at, updated_at,
                    owner_id, name, description, category, product_condition, status
                ) VALUES (%s, %s, %s, NOW(6), NOW(6), %s, %s, %s, %s, %s, %s)
                """,
                (
                    date.today() + timedelta(days=1),
                    date.today() + timedelta(days=30),
                    30000,
                    owner_id,
                    "채팅 브리지 테스트 카메라",
                    "모놀리스 발급과 채팅 소비 연결 검증",
                    "CAMERA",
                    "NORMAL",
                    "ACTIVE",
                ),
            )
            equipment_id = int(cursor.lastrowid)

    ticket_response = request_json(
        "POST",
        f"{MONOLITH_URL}/api/v1/chat/tickets",
        {},
        requester_token,
    )
    assert ticket_response[0] == 200, ticket_response
    ticket = ticket_response[1]["ticket"]

    grant_response = request_json(
        "POST",
        f"{MONOLITH_URL}/api/v1/chat/inquiry-grants",
        {"equipmentId": equipment_id},
        requester_token,
    )
    assert grant_response[0] == 200, grant_response
    grant_token = grant_response[1]["grantToken"]

    created = request_json(
        "POST",
        f"{CHAT_URL}/api/v1/chat/rooms",
        {"grantToken": grant_token},
        ticket,
    )
    assert created[0] == 201, created
    room_id = created[1]["roomId"]

    with mysql_connection("iter_chat") as connection:
        with connection.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute(
                """
                SELECT equipment_id, owner_id, requester_id, stage, rental_id
                FROM chat_rooms WHERE id = %s
                """,
                (room_id,),
            )
            room = cursor.fetchone()
            cursor.execute(
                "SELECT user_id, nickname FROM room_participants WHERE room_id = %s ORDER BY user_id",
                (room_id,),
            )
            participants = cursor.fetchall()

    assert room == {
        "equipment_id": equipment_id,
        "owner_id": owner_id,
        "requester_id": requester_id,
        "stage": "INQUIRY",
        "rental_id": None,
    }
    assert participants == [
        {"user_id": owner_id, "nickname": "chat-owner"},
        {"user_id": requester_id, "nickname": "chat-requester"},
    ]

    # 문의 grant는 GETDEL로 소비되므로 같은 토큰은 재사용할 수 없다.
    reused = request_json(
        "POST",
        f"{CHAT_URL}/api/v1/chat/rooms",
        {"grantToken": grant_token},
        ticket,
    )
    assert reused[0] == 400, reused
    assert reused[1]["code"] == "GRANT_INVALID_OR_EXPIRED"


# IT-AI-013 (INTEGRATION_TEST_DESIGN.md) — 반납 분쟁 신고의 AI 분석 요청.
#
# 신고/분쟁 생성 자체(모놀리스 -> MySQL)와 AiServiceClient의 계약(직접 fixture로 만든
# AiJobRequest -> 실제 ai-service)은 각각 apps/monolith의 RentalPaymentFlowIntegrationTest,
# services/ai의 AiServiceLiveTest(@Tag("system"))가 이미 검증한다. 이 테스트가 메우는 자리는
# 그 둘을 잇는 지점이다: 실제 반납 분쟁으로 만들어진 신고를 관리자가 실제로 접수했을 때
# 모놀리스 -> 실제 ai-service 프로세스 -> report_analysis_job 저장 -> 다시 상태 조회까지
# 끝까지 이어지는지를 real HTTP로 확인한다.
#
# 사진 증빙(receipt_image 등)은 일부러 넣지 않는다: 이 시스템 스택에는 S3/MinIO가 없고,
# 이미지 행이 있으면 RentalReportContextContributor가 실제 S3 HeadObject 호출로 이어져
# 테스트가 인프라 부재로 깨진다. 사진 없는 증빙만으로도 신고 컨텍스트 조립과 AI 작업
# 생성·완료 연결을 증명하는 데는 충분하다.
def test_반납_분쟁_신고는_실제_ai_서비스에서_처리되고_report_analysis_job_에_연결된다() -> None:
    owner_email = f"system-report-owner-{time.time_ns()}@integration.test"
    renter_email = f"system-report-renter-{time.time_ns()}@integration.test"
    password = "Password123!"

    signup(owner_email, password, "분쟁등록자", "dispute-owner")
    signup(renter_email, password, "분쟁대여자", "dispute-renter")
    owner_token = login(owner_email, password)

    start_date = date.today() - timedelta(days=10)
    end_date = date.today() - timedelta(days=5)

    with mysql_connection("iter") as connection:
        owner_id = user_id(connection, owner_email)
        renter_id = user_id(connection, renter_email)
        with connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO equipment (
                    available_from, available_to, daily_price, created_at, updated_at,
                    owner_id, name, description, category, product_condition, status
                ) VALUES (%s, %s, %s, NOW(6), NOW(6), %s, %s, %s, %s, %s, %s)
                """,
                (
                    date.today() - timedelta(days=30),
                    date.today() + timedelta(days=30),
                    30000,
                    owner_id,
                    "시스템 분쟁 테스트 카메라",
                    "AI 신고 분석 시스템 테스트",
                    "CAMERA",
                    "NORMAL",
                    "ACTIVE",
                ),
            )
            equipment_id = cursor.lastrowid

            # return-confirmation이 실제로 검증하는 최소 상태만 심는다: RETURNED 상태의
            # rental과, 존재만 확인하는 receipt/return_receipt 행(사진 없이).
            cursor.execute(
                """
                INSERT INTO rental (
                    daily_price_snapshot, end_date, rental_days, start_date, total_price,
                    approved_at, created_at, equipment_id, renter_id, updated_at, version,
                    product_name_snapshot, category_snapshot, owner_id_snapshot, status
                ) VALUES (%s, %s, %s, %s, %s, NOW(6), NOW(6), %s, %s, NOW(6), 0, %s, %s, %s, 'RETURNED')
                """,
                (30000, end_date, 5, start_date, 150000, equipment_id, renter_id,
                 "시스템 분쟁 테스트 카메라", "CAMERA", owner_id),
            )
            rental_id = cursor.lastrowid

            cursor.execute(
                "INSERT INTO receipt (created_at, received_at, rental_id, product_condition) "
                "VALUES (NOW(6), NOW(6), %s, 'NORMAL')",
                (rental_id,),
            )
            cursor.execute(
                "INSERT INTO return_receipt (created_at, return_date, rental_id, product_condition) "
                "VALUES (NOW(6), CURDATE(), %s, 'DAMAGED')",
                (rental_id,),
            )

    confirmation = request_json(
        "POST",
        f"{MONOLITH_URL}/api/v1/rentals/{rental_id}/return-confirmation",
        {
            "hasIssue": True,
            "disputeReason": "파손 반납",
            "disputeDescription": "시스템 테스트: 반납 시 렌즈 파손이 발견되었습니다.",
        },
        owner_token,
    )
    assert confirmation[0] == 200, confirmation
    report_id = confirmation[1]["reportId"]
    assert report_id is not None

    admin_id = create_admin(f"system-report-admin-{time.time_ns()}@integration.test", "분쟁관리자")
    admin_token = mint_admin_access_token(admin_id)

    accepted = request_json(
        "POST",
        f"{MONOLITH_URL}/api/v1/admin/reports/{report_id}/ai-analysis",
        {
            "description": "관리자가 개인정보를 제거하고 정리한 신고 내용: 반납 시 렌즈 파손 주장.",
            "externalAiConsent": True,
        },
        admin_token,
    )
    assert accepted[0] == 202, accepted
    job_id = accepted[1]["jobId"]
    assert job_id is not None

    deadline = time.monotonic() + 20
    polled = None
    while time.monotonic() < deadline:
        polled = request_json(
            "GET", f"{MONOLITH_URL}/api/v1/admin/reports/{report_id}/ai-analysis", {}, admin_token
        )
        if polled[1].get("status") in ("SUCCEEDED", "FAILED"):
            break
        time.sleep(0.3)

    assert polled is not None and polled[0] == 200, polled
    assert polled[1]["status"] == "SUCCEEDED", polled
    assert polled[1]["analysis"]["suggestedCategory"] == "OTHER"

    with mysql_connection("iter") as connection:
        with connection.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute(
                "SELECT job_id, admin_id FROM report_analysis_job WHERE report_id = %s", (report_id,)
            )
            job_row = cursor.fetchone()
    assert job_row == {"job_id": str(job_id), "admin_id": admin_id}


def wait_for_services() -> None:
    endpoints = [
        f"{MONOLITH_URL}/v3/api-docs",
        f"{CHAT_URL}/health",
        f"{AI_URL}/health/ready",
        "http://127.0.0.1:18090/health",
    ]
    deadline = time.monotonic() + 120
    waiting = set(endpoints)
    while waiting and time.monotonic() < deadline:
        for endpoint in list(waiting):
            try:
                with urllib.request.urlopen(endpoint, timeout=2) as response:
                    if 200 <= response.status < 300:
                        waiting.remove(endpoint)
            except (OSError, urllib.error.URLError):
                pass
        if waiting:
            time.sleep(1)
    assert not waiting, f"서비스 준비 시간 초과: {sorted(waiting)}"


def signup(email: str, password: str, name: str, nickname: str) -> None:
    status, body = request_json(
        "POST",
        f"{MONOLITH_URL}/api/v1/auth/signup",
        {
            "email": email,
            "password": password,
            "name": name,
            "nickname": nickname,
            "phone": "010-1234-5678",
        },
    )
    assert status == 201, body


def login(email: str, password: str) -> str:
    status, body = request_json(
        "POST",
        f"{MONOLITH_URL}/api/v1/auth/login",
        {"email": email, "password": password},
    )
    assert status == 200, body
    return str(body["accessToken"])


def request_json(
    method: str,
    url: str,
    payload: dict[str, Any],
    access_token: str | None = None,
) -> tuple[int, dict[str, Any]]:
    headers = {"Content-Type": "application/json"}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            body = response.read()
            return response.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as error:
        body = error.read()
        return error.code, json.loads(body) if body else {}


def mysql_connection(database: str) -> pymysql.Connection:
    return pymysql.connect(
        host="127.0.0.1",
        port=13306,
        user="root",
        password="",
        database=database,
        autocommit=True,
    )


def user_id(connection: pymysql.Connection, email: str) -> int:
    with connection.cursor() as cursor:
        cursor.execute("SELECT id FROM users WHERE email = %s", email)
        row = cursor.fetchone()
    assert row is not None
    return int(row[0])


# 자가 회원가입(POST /api/v1/auth/signup)은 항상 USER 역할만 만들 수 있어 관리자 계정은
# 여기서 직접 심는다 (apps/monolith의 AdminReportStatusFlowIntegrationTest와 같은 이유·패턴).
def create_admin(email: str, name: str) -> int:
    with mysql_connection("iter") as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO users (
                    point_balance, created_at, updated_at, name, nick_name, phone,
                    email, password, preferred_language, role, status
                ) VALUES (0, NOW(6), NOW(6), %s, %s, '010-0000-0000', %s, 'not-used-in-this-test', 'KO', 'ADMIN', 'ACTIVE')
                """,
                (name, name, email),
            )
            return int(cursor.lastrowid)


# 로그인 API는 USER 역할만 만드는 회원가입과 짝을 이루므로 관리자로 로그인할 방법이 없다.
# JwtTokenProvider(libs/security)가 sub=userId만으로 DB에서 최신 User를 다시 조회해
# 실제 권한을 판단하므로(주석 참고: 정지 등 상태 변화를 토큰 만료 전에도 반영하기 위함),
# role 클레임 값 자체는 인가에 쓰이지 않는다 — 서명·발급자·수신자·유효기간만 진짜와 같으면 된다.
def mint_admin_access_token(user_id_value: int) -> str:
    def b64url(data: bytes) -> str:
        return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

    secret = base64.b64decode(JWT_SECRET_KEY_B64)
    now = int(time.time())
    header = {"alg": "HS512", "typ": "JWT"}
    payload = {
        "iss": JWT_ISSUER,
        "aud": JWT_AUDIENCE,
        "iat": now,
        "exp": now + 3600,
        "jti": str(uuid.uuid4()),
        "sub": str(user_id_value),
        "role": "ADMIN",
        "tokenType": "ACCESS",
    }
    signing_input = (
        f"{b64url(json.dumps(header, separators=(',', ':')).encode('utf-8'))}."
        f"{b64url(json.dumps(payload, separators=(',', ':')).encode('utf-8'))}"
    )
    signature = hmac.new(secret, signing_input.encode("ascii"), hashlib.sha512).digest()
    return f"{signing_input}.{b64url(signature)}"


def stream_pending_count() -> int:
    compose_file = Path(os.environ["ITER_SYSTEM_COMPOSE_FILE"])
    project_name = os.environ["ITER_SYSTEM_COMPOSE_PROJECT"]
    result = subprocess.run(
        [
            "docker",
            "compose",
            "-p",
            project_name,
            "-f",
            str(compose_file),
            "exec",
            "-T",
            "redis",
            "redis-cli",
            "XPENDING",
            "iter.events.chat",
            "chat",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return int(result.stdout.splitlines()[0])
