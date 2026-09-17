from datetime import UTC, datetime

from sqlalchemy import update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.domain.contracts import (
    FeatureType,
    JobAccepted,
    JobRequest,
    JobStatus,
    JobStatusView,
    canonical_sha256,
)
from app.domain.jobs import AiJob


class JobConflictError(Exception):
    pass


class JobNotFoundError(Exception):
    pass


class JobRepository:
    def __init__(self, session: Session, settings: Settings) -> None:
        self.session = session
        self.settings = settings

    # job UUID와 입력 hash를 함께 확인해 같은 요청만 멱등하게 재사용한다.
    def create(self, request: JobRequest) -> JobAccepted:
        payload_hash = canonical_sha256(request.payload)
        existing = self.session.get(AiJob, request.job_id)

        # 같은 job ID와 같은 입력이면 새 행을 만들지 않고 기존 작업을 반환한다.
        if existing is not None:
            self._check_same_request(existing, request, payload_hash)

            return self._accepted(existing, duplicate=True)

        now = utc_now()

        job = AiJob(
            job_id=request.job_id,
            feature_type=request.feature_type.value,
            source_type=request.source.type,
            source_id=request.source.id,
            input_hash=request.input_hash,
            payload_hash=payload_hash,
            status=JobStatus.PENDING.value,
            input_json=request.payload,
            provider=self.settings.provider,
            model=self.settings.openai_model if self.settings.provider == "openai" else "fake-v1",
            created_at=now,
            updated_at=now,
        )

        self.session.add(job)

        try:
            self.session.commit()
        except IntegrityError:
            # 두 요청이 동시에 같은 UUID를 저장한 경우 한쪽 INSERT만 성공한다.
            # 패배한 요청은 rollback 후 먼저 저장된 행을 재사용해 중복 실행을 막는다.
            self.session.rollback()
            existing = self.session.get(AiJob, request.job_id)

            if existing is None:
                raise

            self._check_same_request(existing, request, payload_hash)
            return self._accepted(existing, duplicate=True)

        return self._accepted(job, duplicate=False)

    def get(self, job_id: str) -> JobStatusView:
        return self._to_view(self._find(job_id))

    # PENDING 작업 하나를 원자적으로 선점한다. 동시에 여러 실행 요청이 와도 한 요청만 성공한다.
    def claim_for_processing(self, job_id: str) -> AiJob | None:
        now = utc_now()

        result = self.session.execute(
            update(AiJob)
            .where(
                AiJob.job_id == job_id,
                AiJob.status == JobStatus.PENDING.value,
            )
            .values(
                status=JobStatus.PROCESSING.value,
                started_at=now,
                updated_at=now,
            )
        )
        self.session.commit()

        if result.rowcount != 1:
            # 작업이 없거나 다른 실행 요청이 먼저 선점한 경우이다.
            return None

        return self._find(job_id)

    # 서버가 종료될 때 PROCESSING에 남은 작업을 다시 실행 가능한 PENDING으로 되돌린다.
    def requeue_interrupted_jobs(self) -> int:
        now = utc_now()
        result = self.session.execute(
            update(AiJob)
            .where(AiJob.status == JobStatus.PROCESSING.value)
            .values(
                status=JobStatus.PENDING.value,
                started_at=None,
                updated_at=now,
            )
        )
        self.session.commit()
        return int(result.rowcount or 0)

    # 검증을 통과한 결과와 사용량을 한 번에 성공 상태로 저장한다.
    def succeed(
        self,
        job_id: str,
        result: dict,
        *,
        input_tokens: int | None = None,
        output_tokens: int | None = None,
        estimated_cost_micros: int | None = None,
    ) -> None:
        job = self._find(job_id)
        now = utc_now()

        job.status = JobStatus.SUCCEEDED.value
        job.result_json = result
        job.input_tokens = input_tokens
        job.output_tokens = output_tokens
        job.estimated_cost_micros = estimated_cost_micros
        job.completed_at = now
        job.updated_at = now

        self.session.commit()

    # 사용자에게 노출할 안전한 오류 메시지와 이미 발생한 사용량을 저장한다.
    def fail(
        self,
        job_id: str,
        message: str,
        *,
        input_tokens: int | None = None,
        output_tokens: int | None = None,
        estimated_cost_micros: int | None = None,
    ) -> None:
        job = self._find(job_id)
        now = utc_now()

        job.status = JobStatus.FAILED.value
        # 내부 예외 원문이나 지나치게 긴 provider 응답은 DB와 사용자 화면에 저장하지 않는다.
        job.error_message = message[:500]
        job.input_tokens = input_tokens
        job.output_tokens = output_tokens
        job.estimated_cost_micros = estimated_cost_micros
        job.completed_at = now
        job.updated_at = now

        self.session.commit()

    def _find(self, job_id: str) -> AiJob:
        job = self.session.get(AiJob, job_id)

        if job is None:
            raise JobNotFoundError(job_id)

        return job

    # 같은 UUID를 다른 기능이나 입력에 재사용하면 충돌로 처리한다.
    @staticmethod
    def _check_same_request(job: AiJob, request: JobRequest, payload_hash: str) -> None:
        same_source = (
            job.feature_type == request.feature_type.value
            and job.source_type == request.source.type
            and job.source_id == request.source.id
        )

        same_input = job.input_hash == request.input_hash and job.payload_hash == payload_hash

        if not same_source or not same_input:
            raise JobConflictError("jobId was reused with different input")

    @staticmethod
    def _accepted(job: AiJob, *, duplicate: bool) -> JobAccepted:
        return JobAccepted(job_id=job.job_id, status=JobStatus(job.status), duplicate=duplicate)

    @staticmethod
    def _to_view(job: AiJob) -> JobStatusView:
        return JobStatusView(
            job_id=job.job_id,
            feature_type=FeatureType(job.feature_type),
            status=JobStatus(job.status),
            result=job.result_json,
            error_message=job.error_message,
            provider=job.provider,
            model=job.model,
            input_tokens=job.input_tokens,
            output_tokens=job.output_tokens,
            estimated_cost_micros=job.estimated_cost_micros,
            created_at=job.created_at,
            completed_at=job.completed_at,
        )


# MySQL DATETIME 저장 방식에 맞춰 UTC 기준의 timezone 없는 시각을 반환한다.
def utc_now() -> datetime:
    return datetime.now(UTC).replace(tzinfo=None)
