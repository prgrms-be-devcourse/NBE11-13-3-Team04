import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.config import Settings
from app.domain.jobs import AiJob, Base
from app.infrastructure.mysql import get_session
from app.main import app

FIXTURE = (
    Path(__file__).parents[3]
    / "integration-tests"
    / "contracts"
    / "ai"
    / "jobs"
    / "equipment-draft-job.json"
)


@pytest.fixture
def fake_client(monkeypatch):
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
    monkeypatch.setattr("app.main.recover_interrupted_jobs", lambda: 0)
    monkeypatch.setattr("app.services.job_service.get_settings", lambda: settings)
    monkeypatch.setattr("app.services.job_service.get_session_factory", lambda: session_factory)
    app.dependency_overrides[get_session] = override_session

    try:
        with TestClient(app) as client:
            yield client, engine
    finally:
        app.dependency_overrides.clear()
        engine.dispose()


def test_fake_job_can_be_created_and_queried(fake_client) -> None:
    client, _ = fake_client
    request = json.loads(FIXTURE.read_text(encoding="utf-8"))
    created = client.post("/internal/v1/jobs", json=request)
    fetched = client.get(f"/internal/v1/jobs/{request['jobId']}")

    assert created.status_code == 202
    assert fetched.status_code == 200
    assert fetched.json()["status"] == "SUCCEEDED"
    assert fetched.json()["result"]["name"] is None
    assert "priceSuggestion" not in fetched.json()["result"]


def test_duplicate_and_conflicting_job_requests_preserve_single_result(
    fake_client, monkeypatch
) -> None:
    client, engine = fake_client
    from app.services import job_service

    calls = []
    original_pipeline = job_service.run_pipeline

    def count_pipeline(feature_type, payload, provider):
        calls.append(feature_type)
        return original_pipeline(feature_type, payload, provider)

    monkeypatch.setattr(job_service, "run_pipeline", count_pipeline)
    request = json.loads(FIXTURE.read_text(encoding="utf-8"))

    created = client.post("/internal/v1/jobs", json=request)
    first_result = client.get(f"/internal/v1/jobs/{request['jobId']}").json()
    duplicate = client.post("/internal/v1/jobs", json=request)
    changed = json.loads(json.dumps(request))
    changed["payload"]["hints"]["category"] = "CAMERA"
    conflict = client.post("/internal/v1/jobs", json=changed)
    final_result = client.get(f"/internal/v1/jobs/{request['jobId']}").json()

    assert created.status_code == duplicate.status_code == 202
    assert created.json()["duplicate"] is False
    assert duplicate.json()["duplicate"] is True
    assert duplicate.json()["status"] == "SUCCEEDED"
    assert conflict.status_code == 409
    assert conflict.json()["detail"]["code"] == "IDEMPOTENCY_CONFLICT"
    assert first_result == final_result
    assert len(calls) == 1
    with Session(engine) as session:
        assert session.query(AiJob).count() == 1
