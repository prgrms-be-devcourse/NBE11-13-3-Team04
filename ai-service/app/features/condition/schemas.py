from enum import StrEnum

from pydantic import Field

from app.domain.contracts import ConditionPayload, ConditionPayloadV2, ContractModel

ConditionInput = ConditionPayload | ConditionPayloadV2


# 수령·반납 사진을 비교한 AI의 전체 판단을 구분하는 값이다.
class ConditionAssessment(StrEnum):
    NO_SIGNIFICANT_CHANGE = "NO_SIGNIFICANT_CHANGE"
    CHANGE_SUSPECTED = "CHANGE_SUSPECTED"
    INCONCLUSIVE = "INCONCLUSIVE"


# 사진 비교 결과를 기존 장비 상태 값으로 제안할 때 사용하는 분류이다.
class EquipmentCondition(StrEnum):
    NORMAL = "NORMAL"
    DAMAGED = "DAMAGED"
    DIRTY = "DIRTY"
    MISSING_PART = "MISSING_PART"
    OTHER = "OTHER"


# 두 사진이 AI 비교에 적합한 품질인지 나타내는 값이다.
class QualityStatus(StrEnum):
    ACCEPTED = "ACCEPTED"
    RETAKE_REQUIRED = "RETAKE_REQUIRED"
    INCONCLUSIVE = "INCONCLUSIVE"


# 사진 품질 판정과 직접 확인해야 할 문제 목록을 담는 클래스이다.
class QualityResult(ContractModel):
    status: QualityStatus
    issues: list[str] = Field(max_length=10)


# 수령 사진과 반납 사진 사이에서 발견된 개별 변화 후보를 담는 클래스이다.
class ConditionFinding(ContractModel):
    type: str = Field(max_length=64)
    severity: str = Field(max_length=32)
    capture_slot: str = Field(max_length=64)
    before_image_id: str = Field(max_length=128)
    after_image_id: str = Field(max_length=128)
    description: str = Field(max_length=500)


# 상태 비교 AI가 반환하는 최종 의견, 신뢰도, 품질 및 변화 후보를 담는 클래스이다.
class ConditionResult(ContractModel):
    assessment: ConditionAssessment
    suggested_condition: EquipmentCondition
    reliability: float = Field(ge=0, le=1)
    quality: QualityResult
    findings: list[ConditionFinding] = Field(max_length=20)
    summary: str = Field(max_length=1000)


# 한 촬영 방향에서 수령·반납 사진을 비교한 결과이다.
class ConditionViewResult(ContractModel):
    capture_view: str = Field(pattern=r"^(FRONT|SIDE|REAR)$")
    assessment: ConditionAssessment
    reliability: float = Field(ge=0, le=1)
    quality: QualityResult
    findings: list[ConditionFinding] = Field(max_length=6)
    summary: str = Field(max_length=500)


# 세 촬영 방향의 결과와 전체 참고 의견을 함께 반환하는 V2 결과이다.
class ConditionResultV2(ContractModel):
    assessment: ConditionAssessment
    suggested_condition: EquipmentCondition
    reliability: float = Field(ge=0, le=1)
    quality: QualityResult
    findings: list[ConditionFinding] = Field(max_length=18)
    view_results: list[ConditionViewResult] = Field(min_length=3, max_length=3)
    summary: str = Field(max_length=1000)
