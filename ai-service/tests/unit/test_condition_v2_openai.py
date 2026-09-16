import json
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from pydantic import SecretStr, ValidationError

from app.core.config import Settings
from app.domain.contracts import ConditionPayloadV2
from app.providers.openai_provider import OpenAiProvider

VIEWS = ("FRONT", "SIDE", "REAR")


def image(phase: str, view: str) -> dict:
    return {
        "imageId": f"{phase}-{view.lower()}",
        "objectKey": f"equipment/private/rental-evidence/{phase}/{view.lower()}.jpg",
        "etag": "v1",
        "captureSlot": view,
        "contentType": "image/jpeg",
        "sizeBytes": 100,
    }


def payload(include_listing: bool = True) -> ConditionPayloadV2:
    data = {
        "listingImages": [image("listing", view) for view in VIEWS] if include_listing else [],
        "beforeImages": [image("before", view) for view in VIEWS],
        "afterImages": [image("after", view) for view in VIEWS],
    }
    return ConditionPayloadV2.model_validate(data)


def view_result(view: str, *, damaged: bool = False, usable: bool = True) -> dict:
    finding = {
        "type": "SCRATCH",
        "severity": "LOW",
        "captureSlot": view,
        "beforeImageId": f"before-{view.lower()}",
        "afterImageId": f"after-{view.lower()}",
        "description": f"{view} 반납 사진에서 새로운 선형 흠집이 보입니다.",
    }
    return {
        "captureView": view,
        "assessment": "CHANGE_SUSPECTED" if damaged else "NO_SIGNIFICANT_CHANGE",
        "reliability": 0.8 if usable else 0.2,
        "quality": {
            "status": "ACCEPTED" if usable else "RETAKE_REQUIRED",
            "issues": [] if usable else ["초점이 흐려 같은 부위를 비교하기 어렵습니다."],
        },
        "findings": [finding] if damaged else [],
        "summary": "변화가 의심됩니다." if damaged else "뚜렷한 변화가 보이지 않습니다.",
    }


def model_result(*, damaged_view: str | None = None, unusable_view: str | None = None) -> dict:
    return {
        "assessment": "NO_SIGNIFICANT_CHANGE",
        "suggestedCondition": "NORMAL",
        "reliability": 0.5,
        "quality": {"status": "ACCEPTED", "issues": []},
        "findings": [],
        "viewResults": [
            view_result(
                view,
                damaged=view == damaged_view,
                usable=view != unusable_view,
            )
            for view in VIEWS
        ],
        "summary": "세 방향을 같은 방향끼리 비교했습니다.",
    }


def api_response(view: str, *, damaged: bool = False, usable: bool = True):
    return SimpleNamespace(
        status="completed",
        output_text=json.dumps(view_result(view, damaged=damaged, usable=usable)),
        usage=SimpleNamespace(input_tokens=500, output_tokens=200),
    )


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
    client.responses.create.side_effect = [api_response(view) for view in VIEWS]
    return provider, client, factory


def test_v2_requires_each_manual_capture_view_and_unique_object_keys():
    invalid = {
        "listingImages": [],
        "beforeImages": [
            image("before", "FRONT"),
            image("before", "SIDE"),
            image("before2", "SIDE"),
        ],
        "afterImages": [image("after", view) for view in VIEWS],
    }
    with pytest.raises(ValidationError):
        ConditionPayloadV2.model_validate(invalid)

    duplicate = payload().model_dump(by_alias=True)
    duplicate["afterImages"][0]["objectKey"] = duplicate["beforeImages"][0]["objectKey"]
    with pytest.raises(ValidationError):
        ConditionPayloadV2.model_validate(duplicate)


def test_v2_sends_three_focused_requests_in_view_then_phase_order(configured):
    provider, client, factory = configured

    provider.compare_condition(payload())

    assert client.responses.create.call_count == 3
    factory.assert_called_once()
    for view, call in zip(VIEWS, client.responses.create.call_args_list, strict=True):
        content = call.kwargs["input"][0]["content"]
        labels = [json.loads(item["text"]) for item in content if item["type"] == "input_text"]
        assert [(item["captureSlot"], item["phase"]) for item in labels] == [
            (view, phase) for phase in ("BEFORE", "AFTER")
        ]
        assert len([item for item in content if item["type"] == "input_image"]) == 2
        assert f"이번 요청의 captureView는 {view}" in call.kwargs["instructions"]
        assert call.kwargs["prompt_cache_key"] == "iter:return-condition-v2"
    instructions = client.responses.create.call_args_list[0].kwargs["instructions"]
    assert "통풍구, 스피커 망, 키보드 틈, 직물 짜임" in instructions
    assert "제품 종류와 관계없이 같은 기준" in instructions
    assert "다음 중 하나만 충족해도 finding" in instructions
    assert "도장 벗겨짐 또는 표면 변형을 동반함" in instructions
    assert "AFTER의 네 구역에 독립된 새 선이 없는지" in instructions
    assert "JPEG 압축, 초점과 노이즈 차이도 손상 근거가 아니다" in instructions
    assert "기존 표면 무늬를 불규칙하게 가로지르거나 끊음" in instructions
    assert "가늘고 밝더라도 경계가 선명" in instructions
    assert provider.usage == {"input_tokens": 1500, "output_tokens": 600}


def test_v2_retries_only_inconclusive_view_with_listing(configured):
    provider, client, _ = configured
    client.responses.create.side_effect = [
        api_response("FRONT", usable=False),
        api_response("FRONT", usable=True),
        api_response("SIDE"),
        api_response("REAR"),
    ]

    result = provider.compare_condition(payload())

    assert client.responses.create.call_count == 4
    first_content = client.responses.create.call_args_list[0].kwargs["input"][0]["content"]
    retry_content = client.responses.create.call_args_list[1].kwargs["input"][0]["content"]
    assert len([item for item in first_content if item["type"] == "input_image"]) == 2
    retry_labels = [
        json.loads(item["text"])["phase"]
        for item in retry_content
        if item["type"] == "input_text"
    ]
    assert retry_labels == ["BEFORE", "AFTER", "LISTING"]
    assert result.view_results[0].assessment == "NO_SIGNIFICANT_CHANGE"
    assert provider.usage == {"input_tokens": 2000, "output_tokens": 800}


def test_one_damaged_view_makes_overall_result_change_suspected(configured):
    provider, client, _ = configured
    client.responses.create.side_effect = [
        api_response(view, damaged=view == "SIDE") for view in VIEWS
    ]

    result = provider.compare_condition(payload(include_listing=False))

    assert result.assessment == "CHANGE_SUSPECTED"
    assert result.suggested_condition == "DAMAGED"
    assert len(result.findings) == 1
    assert result.findings[0].capture_slot == "SIDE"


def test_one_unusable_view_does_not_discard_other_comparable_views(configured):
    provider, client, _ = configured
    client.responses.create.side_effect = [
        api_response(view, usable=view != "REAR") for view in VIEWS
    ]

    result = provider.compare_condition(payload(include_listing=False))

    assert result.assessment == "NO_SIGNIFICANT_CHANGE"
    assert result.suggested_condition == "NORMAL"
    assert result.reliability == 0.8
    assert result.view_results[2].assessment == "INCONCLUSIVE"
    assert result.quality.issues == [
        "REAR: 초점이 흐려 같은 부위를 비교하기 어렵습니다."
    ]
