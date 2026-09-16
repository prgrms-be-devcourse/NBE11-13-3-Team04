from functools import lru_cache
from typing import Literal

from pydantic import AliasChoices, Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # 환경변수를 Python 설정값으로 읽는 공통 규칙이다.
    model_config = SettingsConfigDict(
        env_file=("../.env.local", ".env.local", ".env"),
        env_file_encoding="utf-8",
        env_prefix="AI_",
        extra="ignore",
    )

    app_name: str = "iter-ai-service"
    env: Literal["local", "test", "staging", "production"] = "local"
    log_level: str = "INFO"

    database_url: str = (
        "mysql+pymysql://iter_ai_app:change-me@localhost:3306/iter_ai?charset=utf8mb4"
    )

    db_pool_size: int = Field(default=5, ge=1, le=30)  # 평소 유지할 DB 연결 개수다.
    db_max_overflow: int = Field(default=5, ge=0, le=50)  # 사용량 급증 시 추가할 연결 개수다.
    auto_create_tables: bool = True

    internal_auth_enabled: bool = False  # Spring Core 요청에 내부 API key를 요구할지 정한다.
    internal_api_key: SecretStr | None = None  # Core와 AI 서비스가 공유하는 비밀 key다.

    provider: Literal["fake", "openai"] = "fake"  # 가짜 응답 또는 실제 OpenAI 중 하나를 선택한다.
    openai_api_key: SecretStr | None = Field(default=None, validation_alias="OPENAI_API_KEY")
    openai_model: str | None = Field(
        default=None,
        validation_alias=AliasChoices("OPENAI_MODEL", "AI_OPENAI_MODEL"),
    )

    openai_max_output_tokens: int = Field(
        default=800,
        ge=100,
        le=4000,
    )  # 한 번의 응답에서 생성할 최대 출력 token 수다.
    openai_equipment_max_output_tokens: int = Field(
        default=1200,
        ge=400,
        le=4000,
    )  # 모델명·사양·특징을 포함한 장비 초안 전용 출력 token 상한이다.
    openai_equipment_web_search: bool = True  # 식별한 모델의 공식 사양을 웹에서 확인할지 정한다.
    openai_equipment_max_search_calls: int = Field(
        default=1,
        ge=1,
        le=2,
    )  # 장비 초안 한 건에서 허용할 최대 웹 검색 횟수다.
    openai_timeout_seconds: float = Field(
        default=30,
        ge=1,
        le=120,
    )  # OpenAI 응답을 기다릴 최대 시간(초)이다.

    s3_bucket: str = ""  # AI가 분석할 장비 사진이 저장된 S3 bucket 이름이다.
    s3_region: str = "ap-northeast-2"  # S3 bucket의 AWS region이다.
    s3_profile: str | None = None  # 로컬에서 사용할 AWS CLI profile 이름이다.
    s3_key_prefix: str = "equipment/temp/"  # 장비 등록 사진을 읽을 수 있는 S3 경로다.

    equipment_image_max_dimension: int = Field(
        default=1536,
        ge=768,
        le=2048,
    )  # 장비 라벨과 모델 번호를 읽기 위해 유지할 사진의 최대 가로·세로 크기다.
    condition_image_max_dimension: int = Field(
        default=1536,
        ge=768,
        le=2048,
    )  # 작은 긁힘을 비교할 수 있도록 수령·반납 사진에 유지할 최대 크기다.
    report_image_max_dimension: int = Field(
        default=1536,
        ge=768,
        le=2048,
    )  # 신고 검토에서 장비의 작은 글자와 외관 차이를 확인할 사진 크기다.

    s3_equipment_public_key_prefix: str = (
        "equipment/public/"  # 게시된 장비 사진을 읽을 수 있는 공개 경로다.
    )
    s3_condition_key_prefix: str = (
        "equipment/private/rental-evidence/"  # 새 비공개 수령·반납 증빙 경로다.
    )
    s3_legacy_condition_key_prefix: str = (
        "equipment/public/rental-evidence/"  # 이전 공개 증빙을 옮기는 동안만 허용할 경로다.
    )

    input_price_per_million: float | None = Field(default=None, ge=0)
    output_price_per_million: float | None = Field(default=None, ge=0)

    # staging과 production에서는 Core-AI 내부 인증이 빠진 설정을 허용하지 않는다.
    @model_validator(mode="after")
    def validate_shared_environment(self) -> "Settings":
        if self.env in {"staging", "production"} and not self.internal_auth_enabled:
            raise ValueError("internal API key authentication is required outside local/test")
        return self

    # 선택한 provider가 실제 요청을 처리하는 데 필요한 설정을 점검한다.
    def readiness_errors(self) -> list[str]:
        errors = []
        if self.internal_auth_enabled and (
            self.internal_api_key is None or not self.internal_api_key.get_secret_value().strip()
        ):
            errors.append("AI_INTERNAL_API_KEY is required")
        if self.provider == "openai" and self.openai_api_key is None:
            errors.append("OPENAI_API_KEY is required for the OpenAI provider")
        if self.provider == "openai" and not self.openai_model:
            errors.append("OPENAI_MODEL is required for the OpenAI provider")
        if self.provider == "openai" and not self.s3_bucket:
            errors.append("AI_S3_BUCKET is required for equipment images")
        return errors


@lru_cache
def get_settings() -> Settings:
    return Settings()
