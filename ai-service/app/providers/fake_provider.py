from app.domain.contracts import ConditionPayloadV2
from app.features.condition.schemas import (
    ConditionAssessment,
    ConditionInput,
    ConditionResult,
    ConditionResultV2,
    ConditionViewResult,
    EquipmentCondition,
    QualityResult,
    QualityStatus,
)
from app.features.equipment.schemas import EquipmentDraftResult, EquipmentInput
from app.features.reports.schemas import (
    ConfidenceBand,
    ProcessingDisposition,
    ReportCategory,
    ReportInput,
    ReportPriority,
    ReportResult,
)


class FakeAiProvider:
    """Deterministic provider used for contract and MSA round-trip tests."""

    # 실제 사진 분석 없이 수령·반납 비교 API 계약을 확인할 고정 결과를 반환한다.
    def compare_condition(self, request: ConditionInput) -> ConditionResult | ConditionResultV2:
        if isinstance(request, ConditionPayloadV2):
            views = [
                ConditionViewResult(
                    capture_view=view,
                    assessment=ConditionAssessment.INCONCLUSIVE,
                    reliability=0.0,
                    quality=QualityResult(status=QualityStatus.INCONCLUSIVE, issues=[]),
                    findings=[],
                    summary=f"{view} fake 비교 결과입니다.",
                )
                for view in ("FRONT", "SIDE", "REAR")
            ]
            return ConditionResultV2(
                assessment=ConditionAssessment.INCONCLUSIVE,
                suggested_condition=EquipmentCondition.OTHER,
                reliability=0.0,
                quality=QualityResult(status=QualityStatus.INCONCLUSIVE, issues=[]),
                findings=[],
                view_results=views,
                summary="fake provider 결과입니다. 실제 상태 판정에 사용하지 마세요.",
            )
        return ConditionResult(
            assessment=ConditionAssessment.INCONCLUSIVE,
            suggested_condition=EquipmentCondition.OTHER,
            reliability=0.0,
            quality=QualityResult(status=QualityStatus.ACCEPTED, issues=[]),
            findings=[],
            summary="fake provider 결과입니다. 실제 상태 판정에 사용하지 마세요.",
        )

    # 실제 신고 분석 없이 관리자 화면 연동을 확인할 고정 결과를 반환한다.
    def triage_report(self, request: ReportInput) -> ReportResult:
        return ReportResult(
            processing_disposition=ProcessingDisposition.HUMAN_REVIEW_REQUIRED,
            summary="fake provider 결과입니다.",
            suggested_category=ReportCategory.OTHER,
            priority=ReportPriority.NORMAL,
            priority_reasons=[],
            facts=[],
            allegations=[],
            missing_information=["테스트 모드에서는 실제 신고 분석을 수행하지 않습니다."],
            admin_memo_draft=(
                "[테스트 초안] 신고 원문과 관련 증빙을 확인한 후 처리해 주시기 바랍니다."
            ),
            confidence_band=ConfidenceBand.LOW,
        )

    # 실제 사진 분석 없이 장비 초안 화면 연동을 확인할 고정 결과를 반환한다.
    def draft_equipment(self, request: EquipmentInput) -> EquipmentDraftResult:
        return EquipmentDraftResult(
            category=request.hints.category,
            name=request.hints.name,
            manufacturer=None,
            model_name=None,
            identification_status="UNKNOWN",
            identification_evidence=[],
            specifications=[],
            key_features=[],
            reference_sources=[],
            description=None,
            suggested_condition=None,
            condition_detail=None,
            visible_accessories=[],
            uncertainties=["fake provider 결과이며 사진 분석이 수행되지 않음"],
        )
