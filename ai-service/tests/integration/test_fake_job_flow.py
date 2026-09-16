import json
from pathlib import Path

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.config import Settings
from app.domain.jobs import Base
from app.infrastructure.mysql import get_session
from app.main import app

FIXTURE = Path(__file__).parents[1] / "contract" / "fixtures" / "equipment-draft-job.json"


def test_fake_job_can_be_created_and_queried(monkeypatch) -> None:
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
            request = json.loads(FIXTURE.read_text(encoding="utf-8"))
            created = client.post("/internal/v1/jobs", json=request)
            fetched = client.get(f"/internal/v1/jobs/{request['jobId']}")
    finally:
        app.dependency_overrides.clear()

    assert created.status_code == 202
    assert fetched.status_code == 200
    assert fetched.json()["status"] == "SUCCEEDED"
    assert fetched.json()["result"]["name"] is None
    assert "priceSuggestion" not in fetched.json()["result"]
