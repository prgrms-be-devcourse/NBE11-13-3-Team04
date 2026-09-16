import logging

from app.core.config import Settings, get_settings
from app.domain.contracts import FeatureType
from app.infrastructure.job_repository import JobNotFoundError, JobRepository
from app.infrastructure.mysql import get_session_factory
from app.providers.base import AiProvider
from app.providers.fake_provider import FakeAiProvider
from app.providers.openai_provider import OpenAiProvider
from app.runtime.pipeline_registry import run_pipeline

logger = logging.getLogger(__name__)


# 저장된 작업을 처리하고 성공·실패 상태와 토큰 사용량을 AI DB에 기록한다.
def process_job(job_id: str) -> None:
    settings = get_settings()
    session_factory = get_session_factory()
    provider = None

    try:
        with session_factory() as session:
            job = JobRepository(session, settings).claim_for_processing(job_id)
            if job is None:
                return

            feature_type = FeatureType(job.feature_type)
            payload = job.input_json

        # 느린 S3/OpenAI 호출 동안 DB 연결을 점유하지 않도록 위 session을 먼저 닫는다.
        provider = create_provider(settings)
        result = run_pipeline(feature_type, payload, provider)
        usage = getattr(provider, "usage", {})

        # 외부 호출이 끝난 뒤 짧은 새 session에서 결과와 사용량만 저장한다.
        with session_factory() as session:
            JobRepository(session, settings).succeed(
                job_id,
                result,
                **usage,
            )
    except JobNotFoundError:
        logger.warning("AI job not found: jobId=%s", job_id)
    except Exception as exc:
        # 사용자에게는 공통 메시지만 저장하고 실제 예외 종류는 서버 log로만 확인한다.
        logger.error("AI job failed: jobId=%s type=%s", job_id, type(exc).__name__)
        usage = getattr(provider, "usage", {})

        with session_factory() as session:
            try:
                JobRepository(session, settings).fail(
                    job_id,
                    "AI 분석 중 오류가 발생했습니다.",
                    **usage,
                )
            except JobNotFoundError:
                return
    finally:
        if isinstance(provider, OpenAiProvider):
            provider.close()


# 환경 설정에 따라 비용 없는 Fake 또는 실제 OpenAI provider를 선택한다.
def create_provider(settings: Settings) -> AiProvider:
    if settings.provider == "openai":
        return OpenAiProvider(settings)
    return FakeAiProvider()
