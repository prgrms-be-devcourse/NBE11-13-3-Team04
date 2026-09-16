from fastapi import APIRouter, HTTPException, status

from app.core.config import get_settings
from app.infrastructure.mysql import check_database

router = APIRouter(tags=["health"])


@router.get("/health/live")
def live() -> dict[str, str]:
    settings = get_settings()
    return {"status": "UP", "service": settings.app_name}


# 설정과 MySQL 연결이 모두 준비됐을 때만 트래픽 수신 가능 상태를 반환한다.
@router.get("/health/ready")
def ready() -> dict[str, str]:
    settings = get_settings()
    errors = settings.readiness_errors()

    if errors:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "CONFIGURATION_NOT_READY", "errors": errors},
        )
    try:
        check_database()
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "DATABASE_NOT_READY"},
        ) from exc
    return {"status": "UP", "database": "UP"}
