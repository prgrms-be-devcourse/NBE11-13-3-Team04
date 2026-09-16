from enum import StrEnum
from typing import Literal

from pydantic import Field

from app.domain.contracts import ContractModel, ReportPayload

ReportInput = ReportPayload


# AI 분석 자료가 준비됐는지 또는 사람의 추가 검토가 필요한지를 나타내는 값이다.
class ProcessingDisposition(StrEnum):
    ANALYSIS_READY = "ANALYSIS_READY"
    HUMAN_REVIEW_REQUIRED = "HUMAN_REVIEW_REQUIRED"


# 관리자가 먼저 확인할 신고의 검토 우선순위를 나타내는 값이다.
class ReportPriority(StrEnum):
    CRITICAL = "CRITICAL"
    HIGH = "HIGH"
    NORMAL = "NORMAL"
    LOW = "LOW"


# 신고 내용을 검토 목적에 따라 분류한 AI 제안 값이다.
class ReportCategory(StrEnum):
    DAMAGE_OR_CONDITION_MISMATCH = "DAMAGE_OR_CONDITION_MISMATCH"
    LATE_OR_NON_RETURN = "LATE_OR_NON_RETURN"
    FALSE_LISTING_OR_MISREPRESENTATION = "FALSE_LISTING_OR_MISREPRESENTATION"
    HARASSMENT_OR_ABUSE = "HARASSMENT_OR_ABUSE"
    PAYMENT_OR_FRAUD = "PAYMENT_OR_FRAUD"
    SAFETY_OR_ILLEGALITY = "SAFETY_OR_ILLEGALITY"
    OTHER = "OTHER"


# AI가 제안한 신고 분류가 얼마나 명확한지를 단계로 나타내는 값이다.
class ConfidenceBand(StrEnum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


# AI의 추측이 아니라 Core DB에서 확인된 신고 관련 사실 한 건을 담는 클래스이다.
class ReportFact(ContractModel):
    field: str = Field(max_length=128)
    value: str = Field(max_length=128)
    source: Literal["CORE_SNAPSHOT"]


# 신고 검토에 필요한 요약, 분류, 우선순위 및 관리자 메모 초안을 담는 클래스이다.
class ReportResult(ContractModel):
    processing_disposition: ProcessingDisposition
    summary: str = Field(max_length=1000)
    suggested_category: ReportCategory
    priority: ReportPriority
    priority_reasons: list[str] = Field(max_length=10)
    facts: list[ReportFact] = Field(max_length=40)
    allegations: list[str] = Field(max_length=20)
    missing_information: list[str] = Field(max_length=20)
    admin_memo_draft: str = Field(max_length=2000)
    confidence_band: ConfidenceBand
