import json
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from pydantic import SecretStr

from app.core.config import Settings
from app.domain.contracts import ReportPayload
from app.providers.openai_provider import OpenAiProvider


def output():
    return {
        "processingDisposition": "ANALYSIS_READY",
        "summary": "신고자는 장비가 설명과 다르다고 주장합니다.",
        "suggestedCategory": "FALSE_LISTING_OR_MISREPRESENTATION",
        "priority": "NORMAL",
        "priorityReasons": ["등록 내용과 실제 상태의 비교가 필요합니다."],
        "facts": [{"field": "damage", "value": "확정", "source": "CORE_SNAPSHOT"}],
        "allegations": ["장비가 설명과 다르다는 주장입니다."],
        "missingInformation": ["등록 화면과 수령 당시 증빙이 필요합니다."],
        "adminMemoDraft": "양측 설명과 증빙을 확인한 뒤 처리하시기 바랍니다.",
        "confidenceBand": "LOW",
    }


@pytest.fixture
def configured(monkeypatch):
    client = Mock()
    image_loader = Mock()
    image_loader.load.return_value = "data:image/jpeg;base64,test"
    image_factory = Mock(return_value=image_loader)
    monkeypatch.setattr("app.providers.openai_provider.OpenAI", Mock(return_value=client))
    monkeypatch.setattr("app.providers.openai_provider.EquipmentImages", image_factory)
    provider = OpenAiProvider(
        Settings(
            _env_file=None,
            OPENAI_API_KEY=SecretStr("test-only"),
            OPENAI_MODEL="test-model",
            input_price_per_million=1,
            output_price_per_million=2,
        )
    )
    request = ReportPayload(
        reason="검토 요청",
        description="장비가 설명과 다릅니다.",
        target_context={
            "targetType": "EQUIPMENT",
            "reportStatus": "RECEIVED",
            "email": "must-not-send@example.test",
            "systemFacts": {
                "equipmentStatus": "ACTIVE",
                "reportsAgainstEquipment": "2",
                "ownerEmail": "must-not-send@example.test",
            },
            "publicContent": {
                "equipmentName": "Canon EOS R6 Mark II",
                "equipmentDescription": "카메라 대여 장비입니다.",
                "privatePhone": "010-0000-0000",
            },
            "priorConditionAnalysis": {
                "sourceFeature": "RETURN_CONDITION_V1",
                "sourceRentalId": "930023",
                "assessment": "CHANGE_SUSPECTED",
                "suggestedCondition": "DAMAGED",
                "reliability": 0.7,
                "summary": "반납 사진에서 긁힘 후보가 보입니다.",
                "findings": [
                    {
                        "type": "긁힘",
                        "severity": "낮음",
                        "description": "본체 오른쪽 측면 하단에 선형 자국이 보입니다.",
                        "unexpected": "must-not-send",
                    }
                ],
                "unexpected": "must-not-send",
            },
        },
        evidence_images=[
            {
                "imageId": "listing-1",
                "objectKey": "equipment/public/1/photo.png",
                "captureSlot": "LISTING_1",
                "etag": "test-etag",
                "contentType": "image/png",
                "sizeBytes": 1024,
            }
        ],
    )
    client.responses.create.return_value = SimpleNamespace(
        status="completed",
        output_text=json.dumps(output()),
        usage=SimpleNamespace(input_tokens=100, output_tokens=50),
    )
    return provider, client, image_factory, image_loader, request


def test_only_allowlisted_snapshot_and_one_bounded_call(configured):
    provider, client, image_factory, image_loader, request = configured
    result = provider.triage_report(request)
    args = client.responses.create.call_args.kwargs
    serialized_input = json.dumps(args["input"], ensure_ascii=False)
    request_text = json.loads(args["input"][0]["content"][0]["text"])
    assert "must-not-send" not in serialized_input
    assert "010-0000-0000" not in serialized_input
    assert "Canon EOS R6 Mark II" in serialized_input
    assert "LISTING_1" in serialized_input
    evidence_context = request_text["analysisEvidenceContext"]
    assert evidence_context["currentRequestSlots"] == ["LISTING_1"]
    assert evidence_context["priorConditionAnalysisReused"] is True
    assert evidence_context["relatedEvidenceAvailableInAdminView"] is True
    assert "CHANGE_SUSPECTED" in serialized_input
    assert "본체 오른쪽 측면 하단" in serialized_input
    assert args["store"] is False
    assert args["max_output_tokens"] == 800
    assert args["temperature"] == 0
    assert args["text"]["format"]["strict"] is True
    schema = args["text"]["format"]["schema"]
    assert schema["$defs"]["ReportFact"]["properties"]["value"]["type"] == "string"
    assert "신고 승인·기각" in args["instructions"]
    assert "진실성 확률이 아니다" in args["instructions"]
    assert "이번 요청에서 직접 보았다고" in args["instructions"]
    assert "사진이 없다고 단정하지" in args["instructions"]
    assert "현재 사진에서 다시 확인했다고" in args["instructions"]
    assert args["prompt_cache_key"] == "iter:report-triage-v1"
    assert result.processing_disposition == "HUMAN_REVIEW_REQUIRED"
    assert [(fact.field, fact.value) for fact in result.facts] == [
        ("targetType", "EQUIPMENT"),
        ("reportStatus", "RECEIVED"),
        ("equipmentStatus", "ACTIVE"),
        ("reportsAgainstEquipment", "2"),
    ]
    assert "damage" not in [fact.field for fact in result.facts]
    assert provider.usage["estimated_cost_micros"] == 200
    client.responses.create.assert_called_once()
    image_factory.assert_called_once_with(
        provider.settings,
        prefix=(
            "equipment/public/",
            "equipment/private/rental-evidence/",
            "equipment/public/rental-evidence/",
        ),
        max_dimension=1536,
    )
    image_loader.load.assert_called_once_with(request.evidence_images[0])


@pytest.mark.parametrize("kind", ["incomplete", "refused", "invalid_json", "invalid_enum"])
def test_failed_report_response_keeps_usage(configured, kind):
    provider, client, _, _, request = configured
    response = client.responses.create.return_value
    if kind == "incomplete":
        response.status = "incomplete"
    elif kind == "refused":
        response.output_text = ""
    elif kind == "invalid_json":
        response.output_text = "not-json"
    else:
        payload = output()
        payload["priority"] = "BAN_USER"
        response.output_text = json.dumps(payload)
    with pytest.raises(ValueError):
        provider.triage_report(request)
    assert provider.usage["input_tokens"] == 100
    client.responses.create.assert_called_once()


def test_empty_snapshot_does_not_turn_allegations_into_facts(configured):
    provider, _, _, _, request = configured
    request.target_context = {}
    assert provider.triage_report(request).facts == []


def test_prior_condition_result_without_images_is_marked_as_reused_analysis(configured):
    provider, client, image_factory, _, request = configured
    request.evidence_images = []
    payload = output()
    payload["summary"] = (
        "신고자는 장비가 설명과 다르다고 주장합니다. "
        "이번 요청에서는 관련 사진이 없어 직접 확인하지 못했습니다."
    )
    payload["missingInformation"] = [
        "이번 요청에 포함된 반납 전후 사진",
        "장비 수령 및 반납 시점의 사진 자료",
        "거래 날짜 확인 결과",
    ]
    payload["priorityReasons"] = [
        "사진이 이번 요청에 포함되어 있지 않아 직접 확인 자료가 부족합니다."
    ]
    payload["adminMemoDraft"] = (
        "이번 AI 요청에 사진이 포함되지 않았습니다. 양측 설명을 확인하십시오."
    )
    client.responses.create.return_value.output_text = json.dumps(payload)

    result = provider.triage_report(request)

    args = client.responses.create.call_args.kwargs
    serialized_input = json.dumps(args["input"], ensure_ascii=False)
    request_text = json.loads(args["input"][0]["content"][0]["text"])
    evidence_context = request_text["analysisEvidenceContext"]
    assert evidence_context["currentRequestSlots"] == []
    assert evidence_context["priorConditionAnalysisReused"] is True
    assert evidence_context["relatedEvidenceAvailableInAdminView"] is True
    assert "priorConditionAnalysis" in serialized_input
    assert "관련 사진이 없어" not in result.summary
    assert "사진이 포함되지" not in result.admin_memo_draft
    assert "본체 오른쪽 측면 하단에 선형 자국이 보입니다" in result.summary
    assert "본체 오른쪽 측면 하단에 선형 자국이 보입니다" in result.admin_memo_draft
    assert "기존 수령·반납 비교 AI 결과를 참고" in result.summary
    assert "기존 수령·반납 비교 AI 결과를 참고" in result.admin_memo_draft
    assert result.summary.count("본체 오른쪽 측면 하단에 선형 자국이 보입니다") == 1
    assert result.admin_memo_draft.count(
        "본체 오른쪽 측면 하단에 선형 자국이 보입니다"
    ) == 1
    assert all("사진이 이번 요청에" not in item for item in result.priority_reasons)
    assert any("변화 후보와 신고 내용" in item for item in result.priority_reasons)
    assert len(result.priority_reasons) <= 3
    assert "이번 요청에 포함된 반납 전후 사진" not in result.missing_information
    assert all("사진 자료" not in item for item in result.missing_information)
    assert "거래 날짜 확인 결과" in result.missing_information
    assert any(
        "본체 오른쪽 측면 하단에 선형 자국이 보입니다" in item
        for item in result.missing_information
    )
    image_factory.assert_not_called()
