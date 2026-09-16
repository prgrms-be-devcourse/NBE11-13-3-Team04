from typing import Annotated

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.security import verify_internal_api_key
from app.domain.contracts import JobAccepted, JobRequest, JobStatusView
from app.infrastructure.job_repository import (
    JobConflictError,
    JobNotFoundError,
    JobRepository,
)
from app.infrastructure.mysql import get_session
from app.services.job_service import process_job

router = APIRouter(
    prefix="/internal/v1",
    tags=["internal-jobs"],
    dependencies=[Depends(verify_internal_api_key)],
)

# 엔드포인트가 호출될 때 MySQL session을 만들고 응답이 끝나면 자동으로 닫는다.
SessionDependency = Annotated[Session, Depends(get_session)]


# POST /internal/v1/jobs
# Core가 만든 AI 작업을 접수하고 작업 ID와 초기 상태(PENDING)를 반환하는 API이다.
# 같은 UUID와 같은 입력은 기존 작업으로 응답하고, 다른 입력에 같은 UUID를 쓰면 거절한다.
@router.post("/jobs", response_model=JobAccepted, status_code=status.HTTP_202_ACCEPTED)
def create_job(
    payload: JobRequest,
    background_tasks: BackgroundTasks,
    session: SessionDependency,
) -> JobAccepted:
    try:
        # 먼저 요청 원본과 hash를 AI DB에 저장해 재시도하더라도 같은 작업을 식별한다.
        result = JobRepository(session, get_settings()).create(payload)
    except JobConflictError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={"code": "IDEMPOTENCY_CONFLICT"},
        ) from exc

    # 재접수된 PENDING 작업도 다시 실행 후보로 넣는다. 실제 중복 실행은 DB 선점으로 차단한다.
    if result.status.value == "PENDING":
        background_tasks.add_task(process_job, result.job_id)

    return result


# GET /internal/v1/jobs/{job_id}
# Core가 polling하면서 PENDING → PROCESSING → SUCCEEDED/FAILED 상태와 결과를 조회하는 API이다.
# 서버 재시작 뒤 PENDING으로 복구된 작업이면 상태 응답 후 다시 실행한다.
@router.get("/jobs/{job_id}", response_model=JobStatusView)
def get_job(
    job_id: str,
    background_tasks: BackgroundTasks,
    session: SessionDependency,
) -> JobStatusView:
    try:
        result = JobRepository(session, get_settings()).get(job_id)
    except JobNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "JOB_NOT_FOUND"},
        ) from exc

    # 여러 polling 요청이 동시에 등록해도 claim_for_processing에서 하나만 실행된다.
    if result.status.value == "PENDING":
        background_tasks.add_task(process_job, result.job_id)

    return result
