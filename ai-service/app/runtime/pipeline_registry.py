from typing import Any

from app.domain.contracts import (
    ConditionPayload,
    ConditionPayloadV2,
    EquipmentPayload,
    FeatureType,
    ReportPayload,
)
from app.providers.base import AiProvider


# 기능별 payload를 전용 schema로 검증한 뒤 해당 provider 메서드를 실행한다.
def run_pipeline(
    feature_type: FeatureType, payload: dict[str, Any], provider: AiProvider
) -> dict[str, Any]:
    if feature_type == FeatureType.RETURN_CONDITION_V1:
        request = ConditionPayload.model_validate(payload)
        result = provider.compare_condition(request)
    elif feature_type == FeatureType.RETURN_CONDITION_V2:
        request = ConditionPayloadV2.model_validate(payload)
        result = provider.compare_condition(request)
    elif feature_type == FeatureType.REPORT_TRIAGE_V1:
        request = ReportPayload.model_validate(payload)
        result = provider.triage_report(request)
    else:
        request = EquipmentPayload.model_validate(payload)
        result = provider.draft_equipment(request)
    return result.model_dump(mode="json", by_alias=True)
