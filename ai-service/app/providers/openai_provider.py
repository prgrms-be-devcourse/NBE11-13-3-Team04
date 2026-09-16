import json
import logging
import math
import re
from urllib.parse import urlparse

from openai import OpenAI

from app.core.config import Settings
from app.domain.contracts import ConditionPayloadV2, ImageRef
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
from app.features.equipment.schemas import (
    EquipmentDraftResult,
    EquipmentInput,
    EquipmentReferenceSource,
)
from app.features.reports.schemas import (
    ProcessingDisposition,
    ReportFact,
    ReportInput,
    ReportResult,
)
from app.infrastructure.equipment_images import EquipmentImages
from app.providers.prompts import (
    CONDITION_COMPARISON_PROMPT,
    CONDITION_COMPARISON_V2_PROMPT,
    EQUIPMENT_DRAFT_PROMPT,
    EQUIPMENT_ENRICHMENT_PROMPT,
    REPORT_TRIAGE_PROMPT,
)

logger = logging.getLogger(__name__)

# 모델이 실제 값 대신 출력할 수 있는 대표적인 자리표시자다.
_EXACT_PLACEHOLDER_VALUES = {
    "제조사",
    "제조사명",
    "모델",
    "모델명",
    "모델번호",
    "제조사모델명",
    "알수없음",
    "unknown",
    "n/a",
}
_PLACEHOLDER_MARKERS = (
    "센서명",
    "화소수",
    "렌즈명",
    "조리개값",
    "웹사이트.com",
    "상세페이지.com",
    "주요특징",
    "특징1",
    "배치1",
    "제조사로고:제조사명",
    "모델번호:모델번호",
    "제품라벨:제품라벨명",
)

_CATEGORY_NAMES = {
    "LAPTOP": "노트북",
    "TABLET": "태블릿",
    "CAMERA": "카메라",
    "LENS": "카메라 렌즈",
    "MONITOR": "모니터",
    "VR": "VR 기기",
    "GAME_CONSOLE": "게임기",
    "PROJECTOR": "프로젝터",
    "OTHER": "기타 장비",
}

# Spring Core가 개인정보를 제외하고 보낼 수 있도록 합의한 신고 분석 필드다.
_REPORT_SYSTEM_FACT_KEYS = {
    "userStatus",
    "userRole",
    "reportsAgainstUser",
    "completedRentalsAsRenter",
    "completedRentalsAsOwner",
    "overdueRentalsAsRenter",
    "equipmentCategory",
    "equipmentStatus",
    "listedCondition",
    "dailyPrice",
    "reportsAgainstEquipment",
    "rentalRelation",
    "rentalStatus",
    "rentalStartDate",
    "rentalEndDate",
    "rentalDays",
    "dailyPriceSnapshot",
    "totalPrice",
    "paymentStatus",
    "paymentAmount",
    "outboundShippingStatus",
    "outboundDeliveredAt",
    "returnShippingStatus",
    "returnDeliveredAt",
    "receiptCondition",
    "receivedAt",
    "returnCondition",
    "returnDate",
    "relatedDisputeStatus",
}
_REPORT_PUBLIC_CONTENT_KEYS = {
    "targetNickname",
    "equipmentName",
    "equipmentDescription",
    "equipmentConditionDetail",
    "rentalProductName",
    "rentalCategory",
    "receiptConditionDetail",
    "returnConditionDetail",
    "relatedDisputeReason",
    "relatedDisputeDescription",
}

_REUSED_EVIDENCE_NOTICE = (
    "이 분석은 기존 수령·반납 비교 AI 결과를 참고했으며, "
    "최종 판단은 관리자 화면의 원본 증빙 대조가 필요합니다."
)
_REUSED_EVIDENCE_REVIEW_ITEM = "관리자 화면의 관련 거래 증빙 직접 대조 결과"


class OpenAiProvider:
    """OpenAI 호출을 한 곳에 모으기 위한 최소 adapter."""

    # 실제 호출 설정을 검증하고 같은 입력의 결과 일관성을 우선하도록 client를 준비한다.
    def __init__(self, settings: Settings) -> None:
        if settings.openai_api_key is None or not settings.openai_model:
            raise ValueError("OpenAI provider settings are incomplete")
        self.client = OpenAI(
            api_key=settings.openai_api_key.get_secret_value(),
            timeout=settings.openai_timeout_seconds,
            max_retries=0,
        )
        self.model = settings.openai_model
        self.max_output_tokens = settings.openai_max_output_tokens
        self.temperature = 0
        self.settings = settings
        self.usage = {}

    # 선택한 사진의 공통 범위만 비교하고 모순되거나 근거 없는 판정은 보수적으로 정리한다.
    def compare_condition(self, request: ConditionInput) -> ConditionResult | ConditionResultV2:
        if isinstance(request, ConditionPayloadV2):
            return self._compare_condition_v2(request)

        before, after = self._validate_condition_pair(request)
        content = self._build_condition_content(before, after)

        # 자유 형식 문장이 아니라 Pydantic schema와 동일한 JSON만 받도록 제한한다.
        # 이렇게 해야 화면 코드가 응답 형식을 추측하지 않고 안전하게 사용할 수 있다.
        response = self.client.responses.create(
            model=self.model,
            instructions=CONDITION_COMPARISON_PROMPT,
            input=[{"role": "user", "content": content}],
            text={
                "format": {
                    "type": "json_schema",
                    "name": "return_condition",
                    "strict": True,
                    "schema": ConditionResult.model_json_schema(by_alias=True),
                }
            },
            temperature=self.temperature,
            max_output_tokens=self.max_output_tokens,
            prompt_cache_key="iter:return-condition-v1",
            store=False,
        )

        # 결과가 거절되거나 불완전해도 이미 사용된 토큰은 비용 기록에 남겨야 한다.
        self._record_usage(response)
        if response.status != "completed" or not response.output_text:
            raise ValueError("AI response was refused or incomplete")

        result = ConditionResult.model_validate_json(response.output_text)
        return self._normalize_condition_result(result, before, after)

    # 수령·반납 사진을 방향별로 집중 분석하고, 비교가 어려운 방향에만 등록 사진을 보조로 쓴다.
    def _compare_condition_v2(self, request: ConditionPayloadV2) -> ConditionResultV2:
        image_loader = EquipmentImages(
            self.settings,
            prefix=(
                self.settings.s3_equipment_public_key_prefix,
                self.settings.s3_condition_key_prefix,
                self.settings.s3_legacy_condition_key_prefix,
            ),
            max_dimension=self.settings.condition_image_max_dimension,
        )
        view_results = []
        for view in ("FRONT", "SIDE", "REAR"):
            view_result = self._analyze_condition_view(
                request,
                view,
                image_loader,
                include_listing=False,
            )

            # BEFORE/AFTER만으로 비교 자체가 어려울 때만 등록 사진을 추가해 재검토한다.
            # 일반 요청은 한 방향당 두 장만 전송하므로 시각 정보 품질을 유지하면서 입력을 줄인다.
            has_listing = any(image.capture_slot == view for image in request.listing_images)
            needs_listing = (
                view_result.assessment == ConditionAssessment.INCONCLUSIVE
                or view_result.quality.status != QualityStatus.ACCEPTED
            )
            if has_listing and needs_listing:
                view_result = self._analyze_condition_view(
                    request,
                    view,
                    image_loader,
                    include_listing=True,
                )
            view_results.append(view_result)

        result = ConditionResultV2(
            assessment=ConditionAssessment.NO_SIGNIFICANT_CHANGE,
            suggested_condition=EquipmentCondition.NORMAL,
            reliability=0,
            quality=QualityResult(status=QualityStatus.ACCEPTED, issues=[]),
            findings=[],
            view_results=view_results,
            summary="",
        )
        normalized = self._normalize_condition_v2_result(result, request)
        normalized.summary = self._summarize_condition_views(normalized.view_results)
        return normalized

    def _analyze_condition_view(
        self,
        request: ConditionPayloadV2,
        view: str,
        image_loader: EquipmentImages,
        *,
        include_listing: bool,
    ) -> ConditionViewResult:
        content = self._build_condition_view_content(
            request,
            view,
            image_loader,
            include_listing=include_listing,
        )
        reference_note = (
            " BEFORE와 AFTER만으로 비교가 어려워 LISTING을 보조 자료로 추가했다."
            if include_listing
            else " 먼저 BEFORE와 AFTER만 비교한다."
        )
        response = self.client.responses.create(
            model=self.model,
            instructions=(
                f"{CONDITION_COMPARISON_V2_PROMPT}\n"
                f"이번 요청의 captureView는 {view}이다. 결과에도 {view}를 그대로 쓴다."
                f"{reference_note}"
            ),
            input=[{"role": "user", "content": content}],
            text={
                "format": {
                    "type": "json_schema",
                    "name": f"return_condition_{view.lower()}",
                    "strict": True,
                    "schema": ConditionViewResult.model_json_schema(by_alias=True),
                }
            },
            temperature=self.temperature,
            max_output_tokens=self.max_output_tokens,
            prompt_cache_key="iter:return-condition-v2",
            store=False,
        )
        self._record_usage(response)
        if response.status != "completed" or not response.output_text:
            raise ValueError(f"AI response for {view} was refused or incomplete")

        result = ConditionViewResult.model_validate_json(response.output_text)
        if result.capture_view != view:
            raise ValueError(f"AI response returned a different capture view for {view}")
        return result

    # 지정한 한 방향의 수령·반납 사진을 붙여 보내고 등록 사진은 마지막 보조 자료로 둔다.
    def _build_condition_view_content(
        self,
        request: ConditionPayloadV2,
        view: str,
        image_loader: EquipmentImages,
        *,
        include_listing: bool,
    ) -> list[dict]:
        by_phase = {
            "LISTING": {image.capture_slot: image for image in request.listing_images},
            "BEFORE": {image.capture_slot: image for image in request.before_images},
            "AFTER": {image.capture_slot: image for image in request.after_images},
        }
        content = []
        phases = ("BEFORE", "AFTER", "LISTING") if include_listing else ("BEFORE", "AFTER")
        for phase in phases:
            reference = by_phase[phase].get(view)
            if reference is None:
                continue
            content.append(
                {
                    "type": "input_text",
                    "text": json.dumps(
                        {
                            "phase": phase,
                            "captureSlot": view,
                            "imageId": reference.image_id,
                        }
                    ),
                }
            )
            content.append(
                {
                    "type": "input_image",
                    "image_url": image_loader.load(reference),
                    "detail": "high",
                }
            )
        return content

    # 방향별 AI 문장을 그대로 되풀이하지 않고 전체 변화 여부와 사람이 볼 지점만 짧게 합친다.
    def _summarize_condition_views(self, views: list[ConditionViewResult]) -> str:
        labels = {"FRONT": "정면", "SIDE": "측면", "REAR": "후면"}
        changed = [
            view for view in views if view.assessment == ConditionAssessment.CHANGE_SUSPECTED
        ]
        unavailable = [
            view for view in views if view.assessment == ConditionAssessment.INCONCLUSIVE
        ]

        if changed:
            changed_names = "·".join(labels[view.capture_view] for view in changed)
            descriptions = [finding.description for view in changed for finding in view.findings]
            summary = f"{changed_names} 사진에서 수령 이후의 외관 변화 후보가 확인되었습니다."
            if descriptions:
                summary += f" 주요 변화는 {descriptions[0]}"
        else:
            summary = (
                "비교할 수 있는 방향에서는 수령 이후의 뚜렷한 외관 변화가 "
                "확인되지 않았습니다."
            )

        if unavailable:
            names = "·".join(labels[view.capture_view] for view in unavailable)
            summary += f" {names} 사진은 비교가 어려워 직접 확인해야 합니다."
        else:
            summary += " 최종 상태와 책임은 실제 장비를 직접 확인하여 결정해야 합니다."
        return summary[:1000]

    # 모델이 다른 방향의 사진 ID를 섞지 못하게 검사하고 전체 의견을 각도별 결과에서 다시 계산합니다.
    def _normalize_condition_v2_result(
        self,
        result: ConditionResultV2,
        request: ConditionPayloadV2,
    ) -> ConditionResultV2:
        before = {image.capture_slot: image for image in request.before_images}
        after = {image.capture_slot: image for image in request.after_images}
        view_results = {view.capture_view: view for view in result.view_results}
        if set(view_results) != {"FRONT", "SIDE", "REAR"}:
            raise ValueError("V2 result must contain all three capture views")

        normalized_views = []
        all_findings = []
        quality_issues = []
        for capture_view in ("FRONT", "SIDE", "REAR"):
            view = view_results[capture_view]
            expected_before = before[capture_view].image_id
            expected_after = after[capture_view].image_id
            self._validate_view_findings(view, expected_before, expected_after)

            if view.findings:
                view.assessment = ConditionAssessment.CHANGE_SUSPECTED
            elif view.quality.status != QualityStatus.ACCEPTED:
                view.assessment = ConditionAssessment.INCONCLUSIVE
                view.reliability = 0
            else:
                view.assessment = ConditionAssessment.NO_SIGNIFICANT_CHANGE

            normalized_views.append(view)
            all_findings.extend(view.findings)
            quality_issues.extend(f"{capture_view}: {issue}" for issue in view.quality.issues)

        assessments = {view.assessment for view in normalized_views}
        comparable = [
            view for view in normalized_views if view.assessment != ConditionAssessment.INCONCLUSIVE
        ]
        if ConditionAssessment.CHANGE_SUSPECTED in assessments:
            result.assessment = ConditionAssessment.CHANGE_SUSPECTED
            if result.suggested_condition == EquipmentCondition.NORMAL:
                result.suggested_condition = EquipmentCondition.DAMAGED
        elif comparable:
            result.assessment = ConditionAssessment.NO_SIGNIFICANT_CHANGE
            result.suggested_condition = EquipmentCondition.NORMAL
        else:
            result.assessment = ConditionAssessment.INCONCLUSIVE
            result.suggested_condition = EquipmentCondition.OTHER

        result.reliability = (
            round(sum(view.reliability for view in comparable) / len(comparable), 3)
            if comparable
            else 0
        )
        result.quality.status = QualityStatus.ACCEPTED if comparable else QualityStatus.INCONCLUSIVE
        result.quality.issues = quality_issues[:10]
        result.findings = all_findings
        result.view_results = normalized_views
        return result

    def _validate_view_findings(
        self,
        view: ConditionViewResult,
        expected_before: str,
        expected_after: str,
    ) -> None:
        for finding in view.findings:
            if (
                finding.capture_slot != view.capture_view
                or finding.before_image_id != expected_before
                or finding.after_image_id != expected_after
            ):
                raise ValueError("Finding refers to an image from another capture view")

    # 수령·반납 사진이 정확히 한 장씩이며 서로 다른 같은 위치의 사진인지 확인한다.
    def _validate_condition_pair(self, request: ConditionInput) -> tuple[ImageRef, ImageRef]:
        if len(request.before_images) != 1 or len(request.after_images) != 1:
            raise ValueError("Select exactly one before/after pair")

        before, after = request.before_images[0], request.after_images[0]
        if before.capture_slot != after.capture_slot:
            raise ValueError("Image pair slots do not match")
        if before.object_key == after.object_key:
            raise ValueError("Before and after must be different evidence images")

        return before, after

    # 검증된 두 S3 사진과 식별 정보를 OpenAI vision 입력 순서로 구성한다.
    def _build_condition_content(self, before: ImageRef, after: ImageRef) -> list[dict]:
        # 신규 비공개 경로를 기본으로 읽고, 기존 공개 증빙은 마이그레이션 기간에만 함께 허용한다.
        images = EquipmentImages(
            self.settings,
            prefix=(
                self.settings.s3_condition_key_prefix,
                self.settings.s3_legacy_condition_key_prefix,
            ),
            max_dimension=self.settings.condition_image_max_dimension,
        )
        content = []

        for label, reference in [("BEFORE", before), ("AFTER", after)]:
            image_info = {
                "phase": label,
                "imageId": reference.image_id,
                "captureSlot": reference.capture_slot,
            }
            content.append(
                {
                    "type": "input_text",
                    "text": json.dumps(image_info),
                }
            )
            content.append(
                {
                    "type": "input_image",
                    "image_url": images.load(reference),
                    "detail": "high",
                }
            )

        return content

    # 모델 결과의 사진 식별자와 상태 조합을 검사해 화면에 전달할 안전한 의견으로 정리한다.
    def _normalize_condition_result(
        self,
        result: ConditionResult,
        before: ImageRef,
        after: ImageRef,
    ) -> ConditionResult:
        for finding in result.findings:
            if (
                finding.before_image_id != before.image_id
                or finding.after_image_id != after.image_id
                or finding.capture_slot != before.capture_slot
            ):
                raise ValueError("Finding refers to an unknown image")

        unacceptable_quality = result.quality.status != QualityStatus.ACCEPTED
        model_could_not_compare = result.assessment == ConditionAssessment.INCONCLUSIVE
        change_without_evidence = (
            result.assessment == ConditionAssessment.CHANGE_SUSPECTED and not result.findings
        )
        should_be_inconclusive = (
            unacceptable_quality or model_could_not_compare or change_without_evidence
        )

        # 사진 품질이 낮거나 변화 근거가 없으면 모델의 자신감 표현을 그대로 믿지 않는다.
        # 사람이 직접 확인하도록 판정·상태·신뢰도를 하나의 보수적인 값으로 맞춘다.
        if should_be_inconclusive:
            has_specific_explanation = (
                result.assessment == ConditionAssessment.INCONCLUSIVE
                and not result.findings
                and bool(result.summary.strip())
            )
            if not has_specific_explanation:
                result.summary = (
                    "선택한 사진만으로 일관된 비교 의견을 내기 어렵습니다. "
                    "사진과 비교 한계를 직접 확인하십시오."
                )
            result.assessment = ConditionAssessment.INCONCLUSIVE
            if result.quality.status == QualityStatus.ACCEPTED:
                result.quality.status = QualityStatus.INCONCLUSIVE
            result.suggested_condition = EquipmentCondition.OTHER
            result.reliability = 0
            result.findings = []

        # 구체적인 변화 후보가 있으면 모델의 assessment가 달라도 변화 의심으로 통일한다.
        elif result.findings:
            if result.assessment != ConditionAssessment.CHANGE_SUSPECTED:
                result.summary = (
                    "선택한 사진에서 변화 후보가 제시되었습니다. "
                    "사진과 후보 내용을 직접 확인하십시오."
                )
            result.assessment = ConditionAssessment.CHANGE_SUSPECTED

        else:
            result.suggested_condition = EquipmentCondition.NORMAL

        return result

    # 신고 유형별 Core 기록과 관련 사진을 분석하되 최종 처리는 항상 관리자에게 남긴다.
    def triage_report(self, request: ReportInput) -> ReportResult:
        context = self._report_context(request.target_context)
        provided_slots = [image.capture_slot for image in request.evidence_images]
        has_prior_condition_analysis = "priorConditionAnalysis" in context
        content = [
            {
                "type": "input_text",
                "text": json.dumps(
                    {
                        "reason": request.reason,
                        "description": request.description,
                        "targetContext": context,
                        "analysisEvidenceContext": {
                            "currentRequestSlots": provided_slots,
                            "priorConditionAnalysisReused": has_prior_condition_analysis,
                            "relatedEvidenceAvailableInAdminView": has_prior_condition_analysis,
                        },
                    },
                    ensure_ascii=False,
                ),
            }
        ]

        if request.evidence_images:
            images = EquipmentImages(
                self.settings,
                prefix=(
                    self.settings.s3_equipment_public_key_prefix,
                    self.settings.s3_condition_key_prefix,
                    self.settings.s3_legacy_condition_key_prefix,
                ),
                max_dimension=self.settings.report_image_max_dimension,
            )
            # captureSlot으로 등록·수령·반납 단계를 명시해 사진의 시간 순서를 섞지 않는다.
            for reference in request.evidence_images:
                content.append(
                    {
                        "type": "input_text",
                        "text": json.dumps(
                            {
                                "imageId": reference.image_id,
                                "captureSlot": reference.capture_slot,
                            }
                        ),
                    }
                )
                content.append(
                    {
                        "type": "input_image",
                        "image_url": images.load(reference),
                        "detail": "high",
                    }
                )

        response = self.client.responses.create(
            model=self.model,
            instructions=REPORT_TRIAGE_PROMPT,
            input=[{"role": "user", "content": content}],
            text={
                "format": {
                    "type": "json_schema",
                    "name": "report_triage",
                    "strict": True,
                    "schema": ReportResult.model_json_schema(by_alias=True),
                }
            },
            temperature=self.temperature,
            max_output_tokens=self.max_output_tokens,
            prompt_cache_key="iter:report-triage-v1",
            store=False,
        )
        self._record_usage(response)
        if response.status != "completed" or not response.output_text:
            raise ValueError("AI response was refused or incomplete")
        result = ReportResult.model_validate_json(response.output_text)

        # AI가 자동 처리 가능하다고 답해도 최종 결정은 관리자에게 남긴다.
        # facts 또한 모델이 만든 사실이 아니라 Core DB snapshot만 사용한다.
        result.processing_disposition = ProcessingDisposition.HUMAN_REVIEW_REQUIRED
        facts = {
            key: value for key, value in context.items() if key in {"targetType", "reportStatus"}
        }
        facts.update(context.get("systemFacts", {}))
        result.facts = [
            ReportFact(field=key, value=value, source="CORE_SNAPSHOT")
            for key, value in list(facts.items())[:40]
        ]
        if has_prior_condition_analysis and not provided_slots:
            result = self._normalize_reused_report_evidence(
                result,
                context["priorConditionAnalysis"],
            )
        return result

    # 이전 분석만 재사용한 요청에서도 구체적인 변화 후보가 신고 검토 의견에 빠지지 않게 고정한다.
    def _normalize_reused_report_evidence(
        self,
        result: ReportResult,
        prior_analysis: dict,
    ) -> ReportResult:
        result.summary = self._replace_current_request_photo_absence(
            result.summary,
            max_length=1000,
        )
        result.admin_memo_draft = self._replace_current_request_photo_absence(
            result.admin_memo_draft,
            max_length=2000,
        )

        finding_description = self._first_change_finding(prior_analysis)
        review_item = _REUSED_EVIDENCE_REVIEW_ITEM
        if finding_description:
            result.summary = (
                "기존 수령·반납 비교 AI는 다음 변화 후보를 제시했습니다: "
                f"{finding_description.rstrip('.!? ')}. "
                "이 변화 후보가 신고 내용의 손상 주장과 같은 부위·현상인지 "
                f"우선 확인해야 합니다. {_REUSED_EVIDENCE_NOTICE}"
            )
            result.admin_memo_draft = (
                "수령·반납 원본 증빙에서 이전 AI가 제시한 다음 변화 후보를 우선 대조하십시오: "
                f"{finding_description.rstrip('.!? ')}. "
                "신고 내용과 같은 부위·현상인지 확인하고 양측 설명을 함께 검토하십시오. "
                f"{_REUSED_EVIDENCE_NOTICE}"
            )
            review_item = (
                "이전 상태 비교 AI가 제시한 변화 후보의 원본 증빙 대조 결과: "
                f"{finding_description}"
            )

            # 이미 존재하는 원본 증빙을 '사진 부족'으로 평가하지 않고,
            # 변화 후보와 신고 내용의 관련성을 검토 우선순위 근거로 남긴다.
            priority_reasons = [
                item
                for item in result.priority_reasons
                if not self._requests_redundant_photo_submission(item)
            ]
            related_reason = (
                "기존 수령·반납 비교 AI의 변화 후보와 신고 내용이 "
                "같은 부위·현상인지 확인할 필요가 있습니다."
            )
            priority_reasons = [
                item for item in priority_reasons if item != related_reason
            ]
            result.priority_reasons = [related_reason, *priority_reasons][:3]

        # 새 사진 제출 요구는 제거하고 해당 변화 후보의 직접 대조 결과를 요청한다.
        missing_information = [
            item
            for item in result.missing_information
            if not self._requests_redundant_photo_submission(item)
            and item != _REUSED_EVIDENCE_REVIEW_ITEM
        ]
        if review_item not in missing_information:
            missing_information.append(review_item)
        result.missing_information = missing_information[:20]
        return result

    @staticmethod
    def _first_change_finding(prior_analysis: dict) -> str | None:
        if prior_analysis.get("assessment") != "CHANGE_SUSPECTED":
            return None
        findings = prior_analysis.get("findings")
        if not isinstance(findings, list):
            return None
        for finding in findings:
            if isinstance(finding, dict):
                description = finding.get("description")
                if isinstance(description, str) and description.strip():
                    return description.strip()[:500]
        return None

    @staticmethod
    def _requests_redundant_photo_submission(item: str) -> bool:
        if not any(word in item for word in ("사진", "증빙", "증거")):
            return False
        return any(
            word in item
            for word in (
                "추가",
                "제출",
                "첨부",
                "포함",
                "전달",
                "확보",
                "필요",
                "자료",
                "없",
            )
        )

    @staticmethod
    def _replace_current_request_photo_absence(text: str, *, max_length: int) -> str:
        absence_markers = ("없", "미포함", "포함되지", "전달되지")
        sentences = re.split(r"(?<=[.!?])\s+", text.strip())
        kept = [
            sentence
            for sentence in sentences
            if not (
                "사진" in sentence
                and ("이번 요청" in sentence or "이번 AI" in sentence)
                and any(marker in sentence for marker in absence_markers)
            )
        ]
        base = " ".join(kept).strip()
        if _REUSED_EVIDENCE_NOTICE not in base:
            available = max_length - len(_REUSED_EVIDENCE_NOTICE) - 1
            base = base[:available].rstrip()
            base = f"{base} {_REUSED_EVIDENCE_NOTICE}".strip()
        return base

    # 예상하지 않은 키와 긴 문자열을 제거해 외부 AI로 전달되는 범위를 고정한다.
    def _report_context(self, raw_context: dict) -> dict:
        context = {}
        for key in ("targetType", "reportStatus"):
            value = raw_context.get(key)
            if isinstance(value, str):
                context[key] = value[:128]

        system_facts = self._allowlisted_strings(
            raw_context.get("systemFacts"),
            _REPORT_SYSTEM_FACT_KEYS,
            128,
        )
        public_content = self._allowlisted_strings(
            raw_context.get("publicContent"),
            _REPORT_PUBLIC_CONTENT_KEYS,
            1200,
        )
        if system_facts:
            context["systemFacts"] = system_facts
        if public_content:
            context["publicContent"] = public_content

        # 이전 상태 비교 결과는 Core 사실이 아니므로 별도 영역에서 제한된 값만 전달한다.
        prior_analysis = self._prior_condition_analysis(raw_context.get("priorConditionAnalysis"))
        if prior_analysis:
            context["priorConditionAnalysis"] = prior_analysis
        return context

    # 이전 AI 결과에 예상하지 않은 값이 섞여도 신고 분석 prompt로 그대로 전달하지 않는다.
    def _prior_condition_analysis(self, value: object) -> dict:
        if not isinstance(value, dict):
            return {}
        if value.get("sourceFeature") not in {
            "RETURN_CONDITION_V1",
            "RETURN_CONDITION_V2",
        }:
            return {}

        result = self._allowlisted_strings(
            value,
            {
                "sourceFeature",
                "sourceRentalId",
                "completedAt",
                "assessment",
                "suggestedCondition",
                "summary",
            },
            1000,
        )

        reliability = value.get("reliability")
        if (
            isinstance(reliability, (int, float))
            and not isinstance(reliability, bool)
            and math.isfinite(reliability)
            and 0 <= reliability <= 1
        ):
            result["reliability"] = reliability

        raw_findings = value.get("findings")
        findings = []
        if isinstance(raw_findings, list):
            for raw_finding in raw_findings[:3]:
                finding = self._allowlisted_strings(
                    raw_finding,
                    {
                        "type",
                        "severity",
                        "description",
                        "captureSlot",
                        "beforeImageId",
                        "afterImageId",
                    },
                    500,
                )
                if finding:
                    findings.append(finding)
        if findings:
            result["findings"] = findings

        raw_quality = value.get("quality")
        if isinstance(raw_quality, dict):
            quality = self._allowlisted_strings(raw_quality, {"status"}, 64)
            raw_issues = raw_quality.get("issues")
            if isinstance(raw_issues, list):
                issues = [
                    issue.strip()[:200]
                    for issue in raw_issues[:5]
                    if isinstance(issue, str) and issue.strip()
                ]
                if issues:
                    quality["issues"] = issues
            if quality:
                result["quality"] = quality

        raw_view_results = value.get("viewResults")
        view_results = []
        if isinstance(raw_view_results, list):
            for raw_view in raw_view_results[:3]:
                view = self._allowlisted_strings(
                    raw_view,
                    {"captureView", "assessment", "summary"},
                    500,
                )
                if not view:
                    continue
                view_findings = []
                if isinstance(raw_view, dict) and isinstance(raw_view.get("findings"), list):
                    for raw_finding in raw_view["findings"][:3]:
                        finding = self._allowlisted_strings(
                            raw_finding,
                            {
                                "type",
                                "severity",
                                "description",
                                "captureSlot",
                                "beforeImageId",
                                "afterImageId",
                            },
                            500,
                        )
                        if finding:
                            view_findings.append(finding)
                if view_findings:
                    view["findings"] = view_findings
                view_results.append(view)
        if view_results:
            result["viewResults"] = view_results
        return result

    @staticmethod
    def _allowlisted_strings(value: object, allowed_keys: set[str], limit: int) -> dict:
        if not isinstance(value, dict):
            return {}
        return {
            key: item[:limit]
            for key, item in value.items()
            if key in allowed_keys and isinstance(item, str) and item.strip()
        }

    # 등록 사진에서 확인 가능한 정보만 사용해 사람이 수정할 장비 등록 초안을 생성한다.
    def draft_equipment(self, request: EquipmentInput) -> EquipmentDraftResult:
        # 모델 번호처럼 작은 글자를 읽을 수 있도록 이 기능만 더 큰 이미지를 사용한다.
        images = EquipmentImages(
            self.settings,
            max_dimension=self.settings.equipment_image_max_dimension,
        )
        content = []
        # 사진 순서를 유지해 여러 각도의 장비를 하나의 등록 초안으로 분석한다.
        for index, image in enumerate(request.images, start=1):
            content.append(
                {
                    "type": "input_text",
                    "text": f"장비 사진 {index}/{len(request.images)}",
                }
            )
            content.append(
                {"type": "input_image", "image_url": images.load(image), "detail": "high"}
            )

        # 사용자가 입력한 이름은 시각적 근거를 오염시킬 수 있어 모델에 보내지 않는다.
        # 카테고리만 사진 뒤에 미확정 참고값으로 전달하며 최종 분류는 사진을 우선한다.
        if request.hints.category:
            content.append(
                {
                    "type": "input_text",
                    "text": json.dumps(
                        {"unverifiedCategoryHint": request.hints.category},
                        ensure_ascii=False,
                    ),
                }
            )

        response = self.client.responses.create(
            model=self.model,
            instructions=EQUIPMENT_DRAFT_PROMPT,
            input=[{"role": "user", "content": content}],
            text={
                "format": {
                    "type": "json_schema",
                    "name": "equipment_draft",
                    "strict": True,
                    "schema": EquipmentDraftResult.model_json_schema(by_alias=True),
                }
            },
            temperature=self.temperature,
            max_output_tokens=self.settings.openai_equipment_max_output_tokens,
            prompt_cache_key="iter:equipment-draft-v1",
            store=False,
        )
        self._record_usage(response)
        if response.status != "completed" or not response.output_text:
            raise ValueError("AI response was refused or incomplete")
        visual_draft = EquipmentDraftResult.model_validate_json(response.output_text)
        visual_draft = self._normalize_visual_draft(visual_draft)

        # 식별할 모델이 없으면 일반 제품을 억지로 검색하지 않고 사진 기반 초안만 반환한다.
        can_search_model = (
            visual_draft.identification_status in {"MODEL_CONFIRMED", "MODEL_LIKELY"}
            and visual_draft.manufacturer is not None
            and visual_draft.model_name is not None
        )
        if not self.settings.openai_equipment_web_search or not can_search_model:
            return visual_draft

        try:
            return self._enrich_equipment_draft(visual_draft)
        except Exception as exc:
            # 검색 장애가 사진 기반 초안과 기존 수동 등록까지 막지 않게 한다.
            logger.warning("Equipment specification lookup failed: type=%s", type(exc).__name__)
            return visual_draft

    # 첫 단계의 모델 식별 모순과 자리표시자를 제거해 근거 없는 모델 확정을 막는다.
    def _normalize_visual_draft(self, draft: EquipmentDraftResult) -> EquipmentDraftResult:
        identity_values = [draft.name, draft.manufacturer, draft.model_name]
        invalid_identity = any(self._contains_placeholder(value) for value in identity_values)
        missing_identity = draft.manufacturer is None or draft.model_name is None
        has_model_status = draft.identification_status in {"MODEL_CONFIRMED", "MODEL_LIKELY"}

        if invalid_identity or missing_identity or not has_model_status:
            draft.manufacturer = None
            draft.model_name = None
            draft.identification_status = (
                "PRODUCT_TYPE_ONLY" if draft.category is not None else "UNKNOWN"
            )
            # 제품 종류만 확인한 결과에는 사용자 힌트나 추정 브랜드가 섞인 이름을 남기지 않는다.
            draft.name = _CATEGORY_NAMES.get(draft.category) if draft.category is not None else None
            draft.identification_evidence = []

        # 모델을 찾지 못해도 사용자가 빈 제목부터 작성하지 않도록 장비 종류를 기본 이름으로 둔다.
        if draft.name is None and draft.category is not None:
            draft.name = _CATEGORY_NAMES[draft.category]

        draft.identification_evidence = [
            item for item in draft.identification_evidence if not self._contains_placeholder(item)
        ]
        if self._contains_placeholder(draft.description):
            draft.description = None
        if self._contains_placeholder(draft.condition_detail):
            draft.condition_detail = None
        draft.visible_accessories = [
            item for item in draft.visible_accessories if not self._contains_placeholder(item)
        ]

        # 첫 요청은 사진 식별 단계이므로 기술 사양은 반드시 두 번째 검색 단계에서만 채운다.
        draft.specifications = []
        draft.key_features = []
        draft.reference_sources = []
        return draft

    # 식별한 모델명을 텍스트로만 보내 공식 자료의 사양과 특징을 보강한다.
    def _enrich_equipment_draft(
        self,
        visual_draft: EquipmentDraftResult,
    ) -> EquipmentDraftResult:
        response = self.client.responses.create(
            model=self.model,
            instructions=EQUIPMENT_ENRICHMENT_PROMPT,
            input=json.dumps(
                {
                    "category": visual_draft.category,
                    "name": visual_draft.name,
                    "manufacturer": visual_draft.manufacturer,
                    "modelName": visual_draft.model_name,
                    "identificationStatus": visual_draft.identification_status,
                    "searchScope": "PRIMARY_EQUIPMENT_MODEL_ONLY",
                },
                ensure_ascii=False,
            ),
            tools=[{"type": "web_search", "search_context_size": "medium"}],
            tool_choice="required",
            max_tool_calls=self.settings.openai_equipment_max_search_calls,
            include=["web_search_call.action.sources"],
            text={
                "format": {
                    "type": "json_schema",
                    "name": "equipment_draft_with_specs",
                    "strict": True,
                    "schema": EquipmentDraftResult.model_json_schema(by_alias=True),
                }
            },
            temperature=self.temperature,
            max_output_tokens=self.settings.openai_equipment_max_output_tokens,
            prompt_cache_key="iter:equipment-enrichment-v1",
            store=False,
        )
        self._record_usage(response)
        if response.status != "completed" or not response.output_text:
            raise ValueError("AI response was refused or incomplete")

        enriched = EquipmentDraftResult.model_validate_json(response.output_text)
        official_search_sources = self._official_search_sources(
            response,
            visual_draft.manufacturer,
            visual_draft.model_name,
        )

        # 검색 단계가 사진에서 확인한 식별·외관·상태를 바꾸지 못하도록 원래 값을 복원한다.
        enriched.category = visual_draft.category
        enriched.name = visual_draft.name
        enriched.manufacturer = visual_draft.manufacturer
        enriched.model_name = visual_draft.model_name
        enriched.identification_status = visual_draft.identification_status
        enriched.identification_evidence = visual_draft.identification_evidence
        enriched.suggested_condition = visual_draft.suggested_condition
        enriched.condition_detail = visual_draft.condition_detail
        enriched.visible_accessories = visual_draft.visible_accessories

        if self._contains_placeholder(enriched.description):
            enriched.description = visual_draft.description

        enriched.specifications = [
            item
            for item in enriched.specifications
            if not self._contains_placeholder(item.name)
            and not self._contains_placeholder(item.value)
        ]
        enriched.key_features = [
            item for item in enriched.key_features if not self._contains_placeholder(item)
        ]
        enriched.reference_sources = [
            source
            for source in enriched.reference_sources
            if not self._contains_placeholder(source.title)
            and not self._contains_placeholder(source.url)
            and self._is_primary_official_source(
                source.url,
                visual_draft.manufacturer,
                visual_draft.model_name,
            )
        ]
        if not enriched.reference_sources:
            enriched.reference_sources = official_search_sources

        # 출처나 사양이 확인되지 않았으면 검색 설명을 사용하지 않는다.
        if not enriched.reference_sources or not enriched.specifications:
            enriched.specifications = []
            enriched.key_features = []
            enriched.description = visual_draft.description
        else:
            # 공개 설명은 AI의 외형 묘사를 재사용하지 않고 검증된 사양으로 다시 구성한다.
            enriched.description = self._build_specification_description(enriched)

        existing_uncertainties = list(visual_draft.uncertainties)
        for item in enriched.uncertainties:
            if item not in existing_uncertainties and not self._contains_placeholder(item):
                existing_uncertainties.append(item)
        enriched.uncertainties = existing_uncertainties
        return enriched

    @staticmethod
    def _official_search_sources(
        response,
        manufacturer: str | None,
        model_name: str | None,
    ) -> list[EquipmentReferenceSource]:
        """웹 검색 메타데이터에서 제조사 공식 도메인의 출처만 복원한다."""
        if manufacturer is None or model_name is None:
            return []

        sources = []
        seen_urls = set()
        for output in getattr(response, "output", []):
            action = getattr(output, "action", None)
            for source in getattr(action, "sources", None) or []:
                url = getattr(source, "url", "")
                if url in seen_urls or not OpenAiProvider._is_primary_official_source(
                    url,
                    manufacturer,
                    model_name,
                ):
                    continue
                seen_urls.add(url)
                sources.append(
                    EquipmentReferenceSource(
                        title=f"{manufacturer} 공식 자료",
                        url=url,
                    )
                )
                if len(sources) == 3:
                    return sources
        return sources

    @staticmethod
    def _is_primary_official_source(
        url: str,
        manufacturer: str | None,
        model_name: str | None,
    ) -> bool:
        """제조사 공식 도메인의 해당 본체 모델 자료인지 URL 기준으로 확인한다."""
        if manufacturer is None or model_name is None or not url.startswith("https://"):
            return False

        manufacturer_token = "".join(
            character for character in manufacturer.lower() if character.isalnum()
        )
        model_tokens = [
            token
            for token in re.findall(r"[a-z0-9]+", model_name.lower())
            if (len(token) >= 2 or token.isdigit())
            and not (token.isdigit() and len(token) == 4)
        ]
        if len(manufacturer_token) < 3 or not model_tokens:
            return False

        hostname = (urlparse(url).hostname or "").lower()
        compact_hostname = "".join(character for character in hostname if character.isalnum())
        compact_url = "".join(character for character in url.lower() if character.isalnum())
        brand_token = next(
            (token for token in model_tokens if token.isalpha() and len(token) >= 4),
            None,
        )
        official_domain = manufacturer_token in compact_hostname or (
            brand_token is not None and brand_token in compact_hostname
        )
        matched_model_tokens = sum(token in compact_url for token in model_tokens)
        required_model_tokens = max(1, math.ceil(len(model_tokens) * 0.6))
        return official_domain and matched_model_tokens >= required_model_tokens

    @staticmethod
    def _build_specification_description(draft: EquipmentDraftResult) -> str | None:
        """검증된 사양과 기능만으로 대여자가 읽을 상품 소개문을 구성한다."""
        if not draft.specifications or not draft.name:
            return draft.description

        description = (draft.description or "").strip()
        core_specs = draft.specifications[:5]
        compact_description = "".join(description.lower().split())
        included_specs = sum(
            1
            for spec in core_specs
            if "".join(spec.value.lower().split()) in compact_description
            or "".join(spec.name.lower().split()) in compact_description
        )
        banned_observation_phrases = (
            "사진에",
            "사진에서",
            "촬영되어",
            "함께 촬영",
            "버튼과 다이얼",
            "색상",
            "그립 모양",
            "확인되지 않습니다",
        )
        if (
            description.startswith(draft.name)
            and included_specs >= 1
            and not any(phrase in description for phrase in banned_observation_phrases)
        ):
            return description

        if draft.category == "CAMERA":
            return OpenAiProvider._build_camera_specification_description(draft)
        if draft.category == "GAME_CONSOLE":
            return OpenAiProvider._build_game_console_specification_description(draft)

        first_specs = ", ".join(
            f"{spec.name}: {spec.value}" for spec in core_specs[:2]
        )
        sentences = [
            f"{draft.name}입니다.",
            f"핵심 사양은 {first_specs}입니다.",
        ]

        remaining_specs = ", ".join(
            f"{spec.name}: {spec.value}" for spec in core_specs[2:]
        )
        if remaining_specs:
            sentences.append(f"그 밖의 주요 사양은 {remaining_specs}입니다.")

        if draft.key_features:
            feature_text = "; ".join(draft.key_features[:3])
            sentences.append(f"주요 특징은 {feature_text}입니다.")

        # 실제 하자가 전달된 경우에만 상태를 공개 설명에 덧붙인다.
        if draft.condition_detail:
            sentences.append(f"외관 상태는 {draft.condition_detail.rstrip('.')}입니다.")
        return " ".join(sentences)

    @staticmethod
    def _build_camera_specification_description(draft: EquipmentDraftResult) -> str:
        """카메라에서 대여 선택에 필요한 사양을 자연스러운 상품 소개문으로 구성한다."""

        def find_spec(*keywords: str):
            return next(
                (
                    spec
                    for spec in draft.specifications
                    if any(keyword in spec.name for keyword in keywords)
                ),
                None,
            )

        sensor = find_spec("센서")
        pixels = find_spec("유효 화소", "화소수")
        processor = find_spec("처리 엔진", "프로세서")
        continuous = find_spec("연속 촬영")
        video = find_spec("동영상", "영상 해상도")

        sentences = [f"{draft.name}입니다."]
        if sensor and processor:
            sentences.append(
                f"센서는 {sensor.value}, 화상 처리 엔진은 {processor.value}입니다."
            )
        elif sensor:
            sentences.append(f"센서 사양은 {sensor.value}입니다.")
        elif processor:
            sentences.append(f"화상 처리 엔진은 {processor.value}입니다.")

        if pixels:
            sentences.append(f"유효 화소는 {pixels.value}입니다.")

        if continuous:
            sentences.append(f"연속 촬영 사양은 {continuous.value}입니다.")
        if video:
            sentences.append(f"동영상은 {video.value}까지 지원합니다.")

        if draft.key_features:
            feature_text = ", ".join(draft.key_features[:3])
            sentences.append(f"지원 기능에는 {feature_text} 등이 포함됩니다.")

        if draft.condition_detail:
            sentences.append(f"외관 상태는 {draft.condition_detail.rstrip('.')}입니다.")
        return " ".join(sentences)

    @staticmethod
    def _build_game_console_specification_description(
        draft: EquipmentDraftResult,
    ) -> str:
        """게임기의 처리·저장 사양과 체감 기능을 읽기 쉬운 상품 소개문으로 구성한다."""

        def find_spec(*keywords: str):
            return next(
                (
                    spec
                    for spec in draft.specifications
                    if any(keyword.lower() in spec.name.lower() for keyword in keywords)
                ),
                None,
            )

        cpu = find_spec("CPU", "프로세서")
        gpu = find_spec("GPU", "그래픽")
        memory = find_spec("메모리", "RAM")
        storage = find_spec("스토리지", "저장")

        sentences = [f"{draft.name}입니다."]
        if cpu:
            sentences.append(f"CPU는 {cpu.value}입니다.")
        if gpu:
            sentences.append(f"GPU는 {gpu.value}입니다.")
        if memory and storage:
            sentences.append(
                f"시스템 메모리는 {memory.value}, 내장 스토리지는 {storage.value}입니다."
            )
        elif memory:
            sentences.append(f"시스템 메모리는 {memory.value}입니다.")
        elif storage:
            sentences.append(f"내장 스토리지는 {storage.value}입니다.")

        if draft.key_features:
            feature_text = ", ".join(draft.key_features[:3])
            sentences.append(f"지원 기능에는 {feature_text} 등이 포함됩니다.")

        if draft.condition_detail:
            sentences.append(f"외관 상태는 {draft.condition_detail.rstrip('.')}입니다.")
        return " ".join(sentences)

    @staticmethod
    def _contains_placeholder(value: str | None) -> bool:
        if value is None:
            return False
        compact = value.replace(" ", "").lower()
        if compact in _EXACT_PLACEHOLDER_VALUES:
            return True
        return any(marker.replace(" ", "").lower() in compact for marker in _PLACEHOLDER_MARKERS)

    # 결과 검증이나 거절 처리에 실패해도 OpenAI에서 이미 발생한 token은 기록한다.
    def _record_usage(self, response) -> None:
        if response.usage is not None:
            input_tokens = self.usage.get("input_tokens", 0) + response.usage.input_tokens
            output_tokens = self.usage.get("output_tokens", 0) + response.usage.output_tokens
            self.usage = {"input_tokens": input_tokens, "output_tokens": output_tokens}
            input_price = self.settings.input_price_per_million
            output_price = self.settings.output_price_per_million
            if input_price is not None and output_price is not None:
                # 설정 단위가 100만 토큰당 USD이므로 token 수와 곱하면 micro USD가 된다.
                self.usage["estimated_cost_micros"] = math.ceil(
                    input_tokens * input_price + output_tokens * output_price
                )

    def close(self) -> None:
        self.client.close()
