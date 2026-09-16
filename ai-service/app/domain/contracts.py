import hashlib
import json
from datetime import datetime
from enum import StrEnum
from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


def to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


# 모든 API 계약 클래스가 공통으로 사용하는 Pydantic 기본 클래스이다.
# Python의 snake_case 필드를 JSON에서는 Spring 형식인 camelCase로 변환한다.
class ContractModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
    )


# 하나의 AI 서비스에서 제공하는 세 가지 기능을 구분하는 값이다.
class FeatureType(StrEnum):
    RETURN_CONDITION_V1 = "RETURN_CONDITION_V1"
    RETURN_CONDITION_V2 = "RETURN_CONDITION_V2"
    REPORT_TRIAGE_V1 = "REPORT_TRIAGE_V1"
    EQUIPMENT_DRAFT_V1 = "EQUIPMENT_DRAFT_V1"


# AI 작업이 현재 어느 처리 단계에 있는지를 나타내는 값이다.
class JobStatus(StrEnum):
    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


# AI 작업이 Core의 어떤 대여·신고·업로드 묶음에서 생성됐는지 나타낸다.
class SourceRef(ContractModel):
    type: Literal["RENTAL", "REPORT", "UPLOAD_BUNDLE"]
    id: str = Field(min_length=1, max_length=128)


# AI가 읽을 S3 이미지 한 장의 위치, 버전, 형식 및 크기 정보이다.
class ImageRef(ContractModel):
    image_id: str = Field(min_length=1, max_length=128)
    object_key: str = Field(min_length=1, max_length=1024)
    capture_slot: str = Field(min_length=1, max_length=64)
    sha256: str | None = Field(default=None, pattern=r"^(sha256:)?[a-fA-F0-9]{64}$")
    etag: str | None = Field(default=None, max_length=128)
    content_type: Literal["image/jpeg", "image/png", "image/webp"]
    size_bytes: int = Field(gt=0, le=20_000_000)


# 수령 사진 한 장과 반납 사진 한 장으로 구성되는 상태 비교 입력이다.
class ConditionPayload(ContractModel):
    before_images: list[ImageRef] = Field(min_length=1, max_length=1)
    after_images: list[ImageRef] = Field(min_length=1, max_length=1)


# 정면·측면·후면을 같은 방향끼리 비교하는 상태 비교 V2 입력이다.
class ConditionPayloadV2(ContractModel):
    listing_images: list[ImageRef] = Field(default_factory=list, max_length=3)
    before_images: list[ImageRef] = Field(min_length=3, max_length=3)
    after_images: list[ImageRef] = Field(min_length=3, max_length=3)

    @model_validator(mode="after")
    def validate_capture_views(self) -> "ConditionPayloadV2":
        required = {"FRONT", "SIDE", "REAR"}
        before_slots = [image.capture_slot for image in self.before_images]
        after_slots = [image.capture_slot for image in self.after_images]
        listing_slots = [image.capture_slot for image in self.listing_images]

        if set(before_slots) != required or len(set(before_slots)) != 3:
            raise ValueError("beforeImages must contain FRONT, SIDE, and REAR")
        if set(after_slots) != required or len(set(after_slots)) != 3:
            raise ValueError("afterImages must contain FRONT, SIDE, and REAR")
        if listing_slots and (set(listing_slots) != required or len(set(listing_slots)) != 3):
            raise ValueError("listingImages must be empty or contain all three views")

        all_keys = [
            image.object_key
            for image in self.listing_images + self.before_images + self.after_images
        ]
        if len(all_keys) != len(set(all_keys)):
            raise ValueError("condition images must not reuse an objectKey")
        return self


# 관리자가 정리한 신고 내용, Core 상태값 및 관련 사진으로 구성되는 신고 분석 입력이다.
class ReportPayload(ContractModel):
    reason: str = Field(min_length=1, max_length=50)
    description: str = Field(min_length=1, max_length=2000)
    target_context: dict[str, Any]
    evidence_images: list[ImageRef] = Field(default_factory=list, max_length=6)


# 장비 등록 초안을 만들 때 사진과 함께 참고할 사용자 입력값이다.
class EquipmentHints(ContractModel):
    name: str | None = Field(default=None, max_length=100)
    category: str | None = Field(default=None, max_length=64)


# 장비 사진과 선택적인 사용자 참고 정보로 구성되는 장비 초안 입력이다.
class EquipmentPayload(ContractModel):
    images: list[ImageRef] = Field(min_length=1, max_length=5)
    hints: EquipmentHints = Field(default_factory=EquipmentHints)


# Spring Core가 AI 작업을 접수할 때 보내는 세 기능 공통 요청 형식이다.
class JobRequest(ContractModel):
    schema_version: Literal["1.0"] = "1.0"
    job_id: str = Field(min_length=16, max_length=64)
    feature_type: FeatureType
    source: SourceRef
    input_hash: str = Field(pattern=r"^sha256:[a-fA-F0-9]{64}$")
    payload: dict[str, Any]

    # 경로로 안전하게 다시 조회할 수 있도록 job ID를 UUID 표기로 통일한다.
    @field_validator("job_id")
    @classmethod
    def validate_job_id(cls, value: str) -> str:
        return str(UUID(value))

    # 기능별 source와 payload를 검증하고 이전 이미지 계약과 호환되는 형태로 정규화한다.
    @model_validator(mode="after")
    def validate_payload(self) -> "JobRequest":
        expected_sources = {
            FeatureType.RETURN_CONDITION_V1: "RENTAL",
            FeatureType.RETURN_CONDITION_V2: "RENTAL",
            FeatureType.REPORT_TRIAGE_V1: "REPORT",
            FeatureType.EQUIPMENT_DRAFT_V1: "UPLOAD_BUNDLE",
        }
        if self.source.type != expected_sources[self.feature_type]:
            raise ValueError("featureType and source.type do not match")

        payload_types = {
            FeatureType.RETURN_CONDITION_V1: ConditionPayload,
            FeatureType.RETURN_CONDITION_V2: ConditionPayloadV2,
            FeatureType.REPORT_TRIAGE_V1: ReportPayload,
            FeatureType.EQUIPMENT_DRAFT_V1: EquipmentPayload,
        }
        # dict 상태로 들어온 payload를 기능별 모델로 다시 검증해 필드 누락과 초과를 막는다.
        parsed = payload_types[self.feature_type].model_validate(self.payload)
        self.payload = parsed.model_dump(mode="json", by_alias=True)
        # None 선택 필드는 제거해 Spring에서 만든 hash와 Python이 저장할 JSON을 안정화한다.
        for key in ("images", "listingImages", "beforeImages", "afterImages"):
            for image in self.payload.get(key, []):
                for optional_key in ("etag", "sha256"):
                    if image.get(optional_key) is None:
                        image.pop(optional_key, None)
        # 이전에 저장한 신고 작업에는 evidenceImages가 없으므로 빈 기본값은 다시 제거한다.
        if self.feature_type == FeatureType.REPORT_TRIAGE_V1 and not self.payload.get(
            "evidenceImages"
        ):
            self.payload.pop("evidenceImages", None)
        return self


# 작업 접수 API가 반환하는 작업 ID, 초기 상태 및 중복 여부이다.
class JobAccepted(ContractModel):
    job_id: str
    status: JobStatus
    duplicate: bool


# 작업 조회 API가 반환하는 현재 상태, 분석 결과, 오류 및 사용량 정보이다.
class JobStatusView(ContractModel):
    job_id: str
    feature_type: FeatureType
    status: JobStatus
    result: dict[str, Any] | None = None
    error_message: str | None = None
    provider: str
    model: str | None = None
    input_tokens: int | None = None
    output_tokens: int | None = None
    estimated_cost_micros: int | None = None
    created_at: datetime
    completed_at: datetime | None = None


# key 순서와 공백 차이를 제거한 JSON으로 동일 입력을 판별할 hash를 만든다.
def canonical_sha256(value: Any) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode(
        "utf-8"
    )
    return "sha256:" + hashlib.sha256(encoded).hexdigest()
