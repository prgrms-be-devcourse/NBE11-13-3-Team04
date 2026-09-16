from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.internal_jobs import router as jobs_router
from app.core.config import get_settings
from app.core.logging import configure_logging
from app.infrastructure.job_repository import JobRepository
from app.infrastructure.mysql import create_tables, get_session_factory

settings = get_settings()
configure_logging(settings.log_level)


def recover_interrupted_jobs() -> int:
    """서버 재시작 전에 끝나지 못한 작업을 다시 실행 가능한 상태로 바꾼다."""
    with get_session_factory()() as session:
        return JobRepository(session, settings).requeue_interrupted_jobs()


# 애플리케이션 시작 시 설정에 따라 AI 작업 테이블을 준비한다.
@asynccontextmanager
async def lifespan(_app: FastAPI):
    if settings.auto_create_tables:
        create_tables()

    # 정상 종료 전에 멈춘 작업은 다음 상태 조회에서 다시 실행할 수 있도록 대기 상태로 복구한다.
    recover_interrupted_jobs()
    yield


app = FastAPI(
    title="ITer AI Service",
    version="0.1.0",
    lifespan=lifespan,
    # 내부 API 구조가 외부에 노출되지 않도록 공유 환경에서는 Swagger 화면을 끈다.
    docs_url="/docs" if settings.env in {"local", "test"} else None,
    redoc_url=None,
)
app.include_router(health_router)
app.include_router(jobs_router)
