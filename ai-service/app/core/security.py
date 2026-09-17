import hmac

from fastapi import Header, HTTPException, status

from app.core.config import get_settings


# 공유 환경에서는 Spring Core가 보낸 내부 API key를 상수 시간 비교로 검증한다.
def verify_internal_api_key(x_internal_api_key: str | None = Header(default=None)) -> None:
    settings = get_settings()

    if not settings.internal_auth_enabled:
        return

    expected = settings.internal_api_key

    if expected is None or not expected.get_secret_value().strip():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "INTERNAL_AUTH_NOT_CONFIGURED"},
        )

    # 일반 문자열 비교보다 timing 차이가 적은 compare_digest로 key 추측 가능성을 낮춘다.
    if x_internal_api_key is None or not hmac.compare_digest(
        x_internal_api_key.encode("utf-8"), expected.get_secret_value().encode("utf-8")
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"code": "INVALID_INTERNAL_API_KEY"},
        )
