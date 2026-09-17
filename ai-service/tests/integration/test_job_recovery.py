import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.config import Settings
from app.domain.contracts import JobRequest, JobStatus
from app.domain.jobs import AiJob, Base
from app.infrastructure.job_repository import JobRepository
from app.infrastructure.mysql import get_session
from app.main import app

# IT-AI-012 (INTEGRATION_TEST_DESIGN.md) — 처리 중 재시작과 retry.
#
# tests/unit/test_job_repository.py::test_processing_job_is_requeued_after_restart는
# JobRepository.requeue_interrupted_jobs()가 PROCESSING -> PENDING으로 SQL UPDATE만
# 하는 것을 이미 증명한다. 이 테스트는 그 위에서 실제로 빠졌던 부분, 즉 (1) FastAPI
# lifespan(서버 재시작 시점)이 실제로 그 복구를 호출하는지, (2) 복구된 작업이 다음 조회에서
# 다시 실행되어 정확히 한 번만 완료되는지를 끝까지 확인한다.
#
# 기존 tests/integration/test_fake_job_flow.py의 fake_client 픽스처는
# `app.main.recover_interrupted_jobs`를 통째로 no-op으로 바꿔서(재시작 복구 자체를
# 검증 대상에서 제외) 쓰고 있어 이 테스트에는 그대로 쓸 수 없다 — 여기서는 그 훅을
# 살려 둔 별도 픽스처를 쓴다.

FIXTURE = (
    Path(__file__).parents[3]
    / "integration-tests"
    / "contracts"
    / "ai"
    / "jobs"
    / "equipment-draft-job.json"
)


def load_request() -> JobRequest:
    return JobRequest.model_validate(json.loads(FIXTURE.read_text(encoding="utf-8")))


@pytest.fixture
def recovering_client(monkeypatch):
    engine = create_engine(
        "sqlite+pysqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)
    settings = Settings(_env_file=None, env="test", provider="fake")

    def override_session():
        with Session(engine) as session:
            yield session

    monkeypatch.setattr("app.api.internal_jobs.get_settings", lambda: settings)
    monkeypatch.setattr("app.core.security.get_settings", lambda: settings)
    monkeypatch.setattr("app.main.create_tables", lambda: Base.metadata.create_all(engine))
    # 여기가 기존 fake_client 픽스처와의 핵심 차이다: recover_interrupted_jobs를 죽이지
    # 않고, 그 함수가 실제로 쓰는 세션 팩토리만 테스트 엔진으로 돌린다 — "서버 재시작
    # 시점에 정말 복구가 호출되는가"가 이 테스트의 핵심이기 때문이다.
    monkeypatch.setattr("app.main.get_session_factory", lambda: session_factory)
    monkeypatch.setattr("app.services.job_service.get_settings", lambda: settings)
    monkeypatch.setattr("app.services.job_service.get_session_factory", lambda: session_factory)
    app.dependency_overrides[get_session] = override_session

    try:
        yield engine, session_factory, settings
    finally:
        app.dependency_overrides.clear()
        engine.dispose()


def test_처리중_멈춘_작업은_재시작_시점에_대기상태로_복구되고_다음_조회에서_한번만_완료된다(
    recovering_client, monkeypatch
) -> None:
    engine, session_factory, settings = recovering_client
    request = load_request()

    # "서버가 재시작 전에 멈췄다"를 재현한다: 작업을 만들고 처리 선점까지만 해 둔다
    # (claim_for_processing) — 이 시점에서 진짜 서버가 죽었다면 이 행은 PROCESSING에
    # 영원히 남아 다시 실행되지 못했을 것이다.
    with session_factory() as session:
        repository = JobRepository(session, settings)
        repository.create(request)
        claimed = repository.claim_for_processing(request.job_id)
        assert claimed is not None
        assert claimed.status == JobStatus.PROCESSING.value

    from app.services import job_service

    calls = []
    original_pipeline = job_service.run_pipeline

    def count_pipeline(feature_type, payload, provider):
        calls.append(feature_type)
        return original_pipeline(feature_type, payload, provider)

    monkeypatch.setattr(job_service, "run_pipeline", count_pipeline)

    # TestClient를 컨텍스트 매니저로 여는 순간 FastAPI lifespan이 실행되며, 그 안의
    # recover_interrupted_jobs()가 실제 "서버 재시작" 지점이다.
    with TestClient(app) as client:
        with Session(engine) as verify_session:
            recovered = verify_session.get(AiJob, request.job_id)
            assert recovered.status == JobStatus.PENDING.value
            assert recovered.started_at is None

        # PENDING 상태에서의 조회는 백그라운드로 재실행을 예약한다. 이 첫 응답 본문은
        # 백그라운드 작업이 끝나기 전에 이미 만들어진 것이라 여전히 PENDING이지만,
        # TestClient는 응답을 반환하기 전에 그 백그라운드 작업을 동기적으로 끝마친다
        # (test_fake_job_flow.py의 POST -> GET 패턴과 동일) — 그래서 바로 이어지는
        # 두 번째 조회에서는 이미 완료된 결과가 보인다.
        fetched = client.get(f"/internal/v1/jobs/{request.job_id}")
        assert fetched.status_code == 200
        assert fetched.json()["status"] == "PENDING"

        final = client.get(f"/internal/v1/jobs/{request.job_id}")
        assert final.status_code == 200
        assert final.json()["status"] == "SUCCEEDED"

    assert len(calls) == 1
    with Session(engine) as session:
        assert session.query(AiJob).count() == 1
        final = session.get(AiJob, request.job_id)
        assert final.status == JobStatus.SUCCEEDED.value


def test_이미_대기중이거나_완료된_작업은_복구_대상이_아니다(recovering_client) -> None:
    engine, session_factory, settings = recovering_client
    request = load_request()

    with session_factory() as session:
        repository = JobRepository(session, settings)
        accepted = repository.create(request)
        assert accepted.status == JobStatus.PENDING

    with TestClient(app):
        pass

    with Session(engine) as session:
        untouched = session.get(AiJob, request.job_id)
        # PENDING이었던 작업은 복구 대상이 아니므로 started_at 등 상태가 그대로다.
        assert untouched.status == JobStatus.PENDING.value
        assert untouched.started_at is None
