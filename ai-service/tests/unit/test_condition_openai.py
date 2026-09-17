import json
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from pydantic import SecretStr

from app.core.config import Settings
from app.domain.contracts import ConditionPayload
from app.providers.openai_provider import OpenAiProvider


def result():
    return {
        "assessment": "CHANGE_SUSPECTED",
        "suggestedCondition": "DAMAGED",
        "reliability": 0.6,
        "quality": {"status": "ACCEPTED", "issues": []},
        "findings": [
            {
                "type": "SCRATCH",
                "severity": "LOW",
                "captureSlot": "USER_SELECTED",
                "beforeImageId": "before-0",
                "afterImageId": "after-0",
                "description": "반납 사진에서 긁힘으로 보이는 변화가 있습니다.",
            }
        ],
        "summary": "선택한 사진 범위에서 변화가 의심됩니다. 직접 확인이 필요합니다.",
    }


@pytest.fixture
def configured(monkeypatch):
    client = Mock()
    monkeypatch.setattr("app.providers.openai_provider.OpenAI", Mock(return_value=client))
    loader = Mock()
    loader.load.side_effect = lambda ref: "data:image/jpeg;base64," + ref.image_id
    factory = Mock(return_value=loader)
    monkeypatch.setattr("app.providers.openai_provider.EquipmentImages", factory)
    provider = OpenAiProvider(
        Settings(_env_file=None, OPENAI_API_KEY=SecretStr("test-only"), OPENAI_MODEL="test-model")
    )

    def ref(phase):
        return {
            "imageId": phase + "-0",
            "objectKey": "equipment/public/rental-evidence/" + phase + ".jpg",
            "etag": "v1",
            "captureSlot": "USER_SELECTED",
            "contentType": "image/jpeg",
            "sizeBytes": 100,
        }

    request = ConditionPayload.model_validate(
        {"beforeImages": [ref("before")], "afterImages": [ref("after")]}
    )
    client.responses.create.return_value = SimpleNamespace(
        status="completed",
        output_text=json.dumps(result()),
        usage=SimpleNamespace(input_tokens=200, output_tokens=100),
    )
    return provider, client, request, factory


def test_pair_order_prefix_limits_and_usage(configured):
    provider, client, request, factory = configured
    provider.compare_condition(request)
    factory.assert_called_once_with(
        provider.settings,
        prefix=(
            "equipment/private/rental-evidence/",
            "equipment/public/rental-evidence/",
        ),
        max_dimension=1536,
    )
    args = client.responses.create.call_args.kwargs
    content = args["input"][0]["content"]
    assert "BEFORE" in content[0]["text"] and "before-0" in content[1]["image_url"]
    assert "AFTER" in content[2]["text"] and "after-0" in content[3]["image_url"]
    assert content[1]["detail"] == content[3]["detail"] == "high"
    assert args["max_output_tokens"] == 800 and args["store"] is False
    assert args["temperature"] == 0
    assert args["text"]["format"]["strict"] is True
    assert "책임을 확정하지" in args["instructions"]
    assert "공통 부위를 비교할 수 있으면" in args["instructions"]
    assert "그 차이만으로 INCONCLUSIVE를 선택하지 않는다" in args["instructions"]
    assert "일부를 비교하지 못한다는 이유로 나머지 비교도 포기하지 않는다" in args["instructions"]
    assert (
        "추가 사진이 도움이 된다는 이유만으로 RETAKE_REQUIRED를 쓰지 않는다" in args["instructions"]
    )
    assert "서로 다른 면이 촬영됐다는 이유만으로" in args["instructions"]
    assert "전체 형태가 비슷하다는 이유로 바로 변화 없음" in args["instructions"]
    assert "짧은 선형 긁힘" in args["instructions"]
    assert "AFTER에만 밝거나 회색인 선형 자국" in args["instructions"]
    assert provider.usage["input_tokens"] == 200
    client.responses.create.assert_called_once()


@pytest.mark.parametrize("change", ["pair_count", "slot", "same_object"])
def test_invalid_pair_never_calls_images_or_model(configured, change):
    provider, client, request, factory = configured
    if change == "pair_count":
        request.before_images.append(request.before_images[0])
    elif change == "slot":
        request.after_images[0].capture_slot = "OTHER"
    else:
        request.after_images[0].object_key = request.before_images[0].object_key
    with pytest.raises(ValueError):
        provider.compare_condition(request)
    factory.assert_not_called()
    client.responses.create.assert_not_called()


@pytest.mark.parametrize("quality", ["RETAKE_REQUIRED", "INCONCLUSIVE"])
def test_poor_quality_cannot_be_treated_as_damage(configured, quality):
    provider, client, request, _ = configured
    payload = result()
    payload["quality"]["status"] = quality
    client.responses.create.return_value.output_text = json.dumps(payload)
    response = provider.compare_condition(request)
    assert response.assessment == "INCONCLUSIVE"
    assert response.suggested_condition == "OTHER" and response.reliability == 0
    assert response.findings == []


@pytest.mark.parametrize("invalid", ["image_id", "incomplete", "empty", "json"])
def test_invalid_response_keeps_usage(configured, invalid):
    provider, client, request, _ = configured
    response = client.responses.create.return_value
    if invalid == "image_id":
        payload = result()
        payload["findings"][0]["afterImageId"] = "invented"
        response.output_text = json.dumps(payload)
    elif invalid == "incomplete":
        response.status = "incomplete"
    else:
        response.output_text = "" if invalid == "empty" else "bad-json"
    with pytest.raises(ValueError):
        provider.compare_condition(request)
    assert provider.usage["output_tokens"] == 100


def test_change_without_finding_is_inconclusive(configured):
    provider, client, request, _ = configured
    payload = result()
    payload["findings"] = []
    client.responses.create.return_value.output_text = json.dumps(payload)
    assert provider.compare_condition(request).assessment == "INCONCLUSIVE"


@pytest.mark.parametrize("assessment", ["NO_SIGNIFICANT_CHANGE", "CHANGE_SUSPECTED"])
def test_partial_comparison_keeps_opinion_and_limitations(configured, assessment):
    provider, client, request, _ = configured
    payload = result()
    payload["assessment"] = assessment
    payload["quality"]["issues"] = ["촬영 각도가 달라 뒷면은 비교하지 못했습니다."]
    payload["reliability"] = 0.3
    if assessment == "NO_SIGNIFICANT_CHANGE":
        payload["findings"] = []
        payload["summary"] = (
            "공통으로 보이는 외장에서는 뚜렷한 변화가 보이지 않습니다. 뒷면은 확인이 필요합니다."
        )
    client.responses.create.return_value.output_text = json.dumps(payload)
    response = provider.compare_condition(request)
    assert response.assessment == assessment
    assert response.quality.status == "ACCEPTED"
    assert response.quality.issues == payload["quality"]["issues"]
    assert response.summary == payload["summary"]
    assert response.reliability == 0.3
    assert len(response.findings) == len(payload["findings"])
    if assessment == "NO_SIGNIFICANT_CHANGE":
        assert response.suggested_condition == "NORMAL"


@pytest.mark.parametrize("quality", ["RETAKE_REQUIRED", "INCONCLUSIVE"])
def test_genuinely_uncomparable_pair_keeps_specific_explanation(configured, quality):
    provider, client, request, _ = configured
    payload = result()
    payload.update(
        assessment="INCONCLUSIVE",
        findings=[],
        summary=(
            "반납 사진에서 외장 일부는 보이지만 심하게 흐려 "
            "수령 사진과 같은 부위를 식별하기 어렵습니다."
        ),
    )
    payload["quality"] = {"status": quality, "issues": ["공통 비교 부위를 식별할 수 없습니다."]}
    client.responses.create.return_value.output_text = json.dumps(payload)
    response = provider.compare_condition(request)
    assert response.assessment == "INCONCLUSIVE"
    assert response.suggested_condition == "OTHER" and response.reliability == 0
    assert response.findings == []
    assert response.summary == payload["summary"]


def test_contradictory_damage_summary_is_not_preserved(configured):
    provider, client, request, _ = configured
    payload = result()
    payload["quality"]["status"] = "RETAKE_REQUIRED"
    payload["summary"] = "새로운 손상이 확실합니다."
    client.responses.create.return_value.output_text = json.dumps(payload)
    response = provider.compare_condition(request)
    assert response.assessment == "INCONCLUSIVE" and response.findings == []
    assert response.summary != payload["summary"]


def test_inconclusive_assessment_does_not_keep_accepted_quality(configured):
    # 실제 재검증에서 발견한 ACCEPTED + INCONCLUSIVE 조합. 판정을 억지로 긍정으로 바꾸지 않는다.
    provider, client, request, _ = configured
    payload = result()
    payload.update(assessment="INCONCLUSIVE", findings=[], summary="공통 부위를 찾기 어렵습니다.")
    client.responses.create.return_value.output_text = json.dumps(payload)
    response = provider.compare_condition(request)
    assert response.assessment == "INCONCLUSIVE"
    assert response.quality.status == "INCONCLUSIVE"
    assert response.summary == payload["summary"]
