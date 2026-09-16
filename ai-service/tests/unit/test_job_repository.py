import json
from pathlib import Path

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.domain.contracts import JobRequest, JobStatus
from app.domain.jobs import Base
from app.infrastructure.job_repository import JobConflictError, JobRepository

FIXTURE = Path(__file__).parents[1] / "contract" / "fixtures" / "report-triage-job.json"


def load_request() -> JobRequest:
    return JobRequest.model_validate(json.loads(FIXTURE.read_text(encoding="utf-8")))


@pytest.fixture
def session() -> Session:
    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    with Session(engine, expire_on_commit=False) as value:
        yield value


def test_create_and_same_job_retry_are_idempotent(session: Session) -> None:
    repository = JobRepository(session, Settings(_env_file=None))
    request = load_request()

    first = repository.create(request)
    retried = repository.create(request)

    assert first.status == JobStatus.PENDING
    assert first.duplicate is False
    assert retried.duplicate is True


def test_failed_job_keeps_known_token_usage(session: Session) -> None:
    repository = JobRepository(session, Settings(_env_file=None))
    request = load_request()
    repository.create(request)
    repository.fail(request.job_id, "invalid output", input_tokens=100, output_tokens=30)
    result = repository.get(request.job_id)
    assert result.status == JobStatus.FAILED
    assert result.input_tokens == 100
    assert result.output_tokens == 30
    assert result.estimated_cost_micros is None


def test_only_one_request_can_claim_a_pending_job(session: Session) -> None:
    repository = JobRepository(session, Settings(_env_file=None))
    request = load_request()
    repository.create(request)

    claimed = repository.claim_for_processing(request.job_id)
    duplicate_claim = repository.claim_for_processing(request.job_id)

    assert claimed is not None
    assert claimed.status == JobStatus.PROCESSING
    assert duplicate_claim is None


def test_processing_job_is_requeued_after_restart(session: Session) -> None:
    repository = JobRepository(session, Settings(_env_file=None))
    request = load_request()
    repository.create(request)
    repository.claim_for_processing(request.job_id)

    recovered_count = repository.requeue_interrupted_jobs()
    recovered = repository.get(request.job_id)

    assert recovered_count == 1
    assert recovered.status == JobStatus.PENDING


def test_same_job_id_with_changed_payload_conflicts(session: Session) -> None:
    repository = JobRepository(session, Settings(_env_file=None))
    request = load_request()
    repository.create(request)
    changed_data = request.model_dump(mode="json", by_alias=True)
    changed_data["payload"]["description"] = "변경된 신고 설명"

    with pytest.raises(JobConflictError):
        repository.create(JobRequest.model_validate(changed_data))


def test_different_job_id_creates_a_new_job(session: Session) -> None:
    repository = JobRepository(session, Settings(_env_file=None))
    first_request = load_request()
    repository.create(first_request)
    second_data = first_request.model_dump(mode="json", by_alias=True)
    second_data["jobId"] = "01980000-0000-7000-8000-000000000099"

    second = repository.create(JobRequest.model_validate(second_data))

    assert second.duplicate is False
    assert second.job_id != first_request.job_id


def test_same_job_id_with_changed_source_conflicts(session: Session) -> None:
    repository = JobRepository(session, Settings(_env_file=None))
    request = load_request()
    repository.create(request)
    changed_data = request.model_dump(mode="json", by_alias=True)
    changed_data["source"]["id"] = "another-report"
    with pytest.raises(JobConflictError):
        repository.create(JobRequest.model_validate(changed_data))
