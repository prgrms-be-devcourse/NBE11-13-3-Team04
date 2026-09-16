import json
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from pydantic import SecretStr, ValidationError

from app.core.config import Settings
from app.domain.contracts import EquipmentPayload
from app.providers.openai_provider import OpenAiProvider


@pytest.fixture
def configured(monkeypatch):
    client = Mock()
    monkeypatch.setattr("app.providers.openai_provider.OpenAI", Mock(return_value=client))
    loader = Mock()
    loader.load.return_value = "data:image/jpeg;base64,test"
    monkeypatch.setattr("app.providers.openai_provider.EquipmentImages", Mock(return_value=loader))
    settings = Settings(
        _env_file=None,
        OPENAI_API_KEY=SecretStr("test-only"),
        OPENAI_MODEL="test-model",
        openai_equipment_web_search=False,
        input_price_per_million=1,
        output_price_per_million=2,
    )
    provider = OpenAiProvider(settings)
    request = EquipmentPayload.model_validate(
        {
            "images": [
                {
                    "imageId": "1",
                    "objectKey": "equipment/temp/1/test.jpg",
                    "etag": "etag",
                    "captureSlot": "OVERVIEW",
                    "contentType": "image/jpeg",
                    "sizeBytes": 123,
                }
            ]
        }
    )
    return provider, client, request


def response(text, status="completed"):
    return SimpleNamespace(
        status=status,
        output_text=text,
        usage=SimpleNamespace(input_tokens=100, output_tokens=50),
        output=[],
    )


def valid_result():
    return {
        "category": "CAMERA",
        "name": "Sony Alpha 7 III",
        "manufacturer": "Sony",
        "modelName": "Alpha 7 III",
        "identificationStatus": "MODEL_LIKELY",
        "identificationEvidence": ["전면 α7 III 표기"],
        "specifications": [{"name": "센서", "value": "35mm 풀프레임 CMOS"}],
        "keyFeatures": ["5축 손떨림 보정"],
        "referenceSources": [
            {
                "title": "Sony Alpha 7 III 제품 정보",
                "url": "https://www.sony.example/alpha-7-iii",
            }
        ],
        "description": "카메라 외관",
        "suggestedCondition": None,
        "conditionDetail": None,
        "visibleAccessories": [],
        "uncertainties": ["작동 여부는 확인할 수 없음"],
    }


def test_structured_image_request_limits_and_usage(configured):
    provider, client, request = configured
    client.responses.create.return_value = response(json.dumps(valid_result()))
    result = provider.draft_equipment(request)
    assert result.category == "CAMERA"
    args = client.responses.create.call_args.kwargs
    assert args["store"] is False
    assert args["max_output_tokens"] == 1200
    assert args["temperature"] == 0
    assert args["input"][0]["content"][1]["detail"] == "high"
    assert "tools" not in args
    assert "max_tool_calls" not in args
    assert args["input"][0]["content"][0]["text"] == "장비 사진 1/1"
    assert args["prompt_cache_key"] == "iter:equipment-draft-v1"
    schema = args["text"]["format"]["schema"]
    assert schema["additionalProperties"] is False
    assert set(schema["required"]) == set(schema["properties"])
    assert "price" not in schema["properties"]
    assert provider.usage == {
        "input_tokens": 100,
        "output_tokens": 50,
        "estimated_cost_micros": 200,
    }


def test_user_name_hint_cannot_become_visual_identity_evidence(configured):
    provider, client, request = configured
    request.hints.name = "Canon 카메라"
    payload = valid_result()
    payload.update(
        {
            "name": "Canon 미확인 모델 카메라",
            "manufacturer": None,
            "modelName": None,
            "identificationStatus": "PRODUCT_TYPE_ONLY",
            "identificationEvidence": ["Canon 로고가 보임", "렌즈가 장착된 카메라 본체"],
        }
    )
    client.responses.create.return_value = response(json.dumps(payload))

    result = provider.draft_equipment(request)

    serialized_input = json.dumps(
        client.responses.create.call_args.kwargs["input"],
        ensure_ascii=False,
    )
    assert "Canon 카메라" not in serialized_input
    assert result.name == "카메라"
    assert result.manufacturer is None
    assert result.model_name is None
    assert result.identification_status == "PRODUCT_TYPE_ONLY"
    assert result.identification_evidence == []


@pytest.mark.parametrize(
    "text,status", [("", "completed"), ("{}", "incomplete"), ("not-json", "completed")]
)
def test_invalid_or_incomplete_response_keeps_usage(configured, text, status):
    provider, client, request = configured
    client.responses.create.return_value = response(text, status)
    with pytest.raises(ValueError):
        provider.draft_equipment(request)
    assert provider.usage["input_tokens"] == 100


def test_rental_listing_style_and_fact_boundaries_are_sent(configured):
    provider, client, request = configured
    client.responses.create.return_value = response(json.dumps(valid_result()))
    provider.draft_equipment(request)
    instructions = client.responses.create.call_args.kwargs["instructions"]
    assert "판매글이 아닌 대여글" in instructions
    assert "격식 있는 하십시오체" in instructions
    assert "3~5문장" in instructions
    assert "공백 포함 150~350자" in instructions
    assert "350자를 넘기지" in instructions
    assert "정보가 부족하면 목표 분량보다 짧게" in instructions
    assert "기술 사양을 단정하지" in instructions
    assert "같은 내용을 반복하거나" in instructions
    assert "구매 이력, 사용 기간을 지어내지" in instructions
    assert "대여에 포함된다고 약속하지" in instructions
    assert "촬영 배경과 주변 물건은 visibleAccessories" in instructions
    assert "사진마다 별도 구성품으로 세지 않고 한 개" in instructions
    assert "두 모델을 모두 나열하거나 두 개가 포함된 것으로 단정하지" in instructions
    assert "description과 visibleAccessories에서 같은 명칭" in instructions
    assert "서로 다른 명칭을 섞지 말고 외형만 설명" in instructions
    assert "uncertainties로 분리" in instructions
    assert "사진을 보면 바로 알 수 있는 구조는 설명하지" in instructions
    assert "그립이 편하다는" in instructions
    assert "MODEL_CONFIRMED" in instructions
    assert "외부 자료를 사용하지 않고 사진 자체" in instructions
    assert "비슷하게 생긴 인기 모델을 임의로 선택" in instructions
    assert "자리표시자나 예시 문자열을 절대 출력하지" in instructions
    client.responses.create.assert_called_once()


def test_visual_description_is_preserved_before_enrichment(configured):
    provider, client, request = configured
    payload = valid_result()
    payload["description"] = (
        "Sony Alpha 7 III 카메라를 대여합니다. 사진에는 렌즈 캡과 스트랩이 함께 "
        "보입니다. 제공된 사진 범위에서는 바디 외관에 눈에 띄는 파손이나 심한 흠집이 "
        "확인되지 않습니다. 작동 상태와 사진에 보이지 않는 면은 직접 확인이 필요합니다."
    )
    assert len(payload["description"]) <= 350
    client.responses.create.return_value = response(json.dumps(payload))
    result = provider.draft_equipment(request)
    assert result.description == payload["description"]
    assert result.uncertainties == payload["uncertainties"]


def test_unknown_category_is_rejected(configured):
    provider, client, request = configured
    result = valid_result()
    result["category"] = "invented-category"
    client.responses.create.return_value = response(json.dumps(result))
    with pytest.raises(ValidationError):
        provider.draft_equipment(request)


def test_unconfigured_price_stays_unknown(configured):
    provider, client, request = configured
    provider.settings.input_price_per_million = None
    client.responses.create.return_value = response(json.dumps(valid_result()))
    provider.draft_equipment(request)
    assert "estimated_cost_micros" not in provider.usage


def test_identified_model_is_enriched_with_limited_web_search(configured):
    provider, client, request = configured
    provider.settings.openai_equipment_web_search = True
    client.responses.create.side_effect = [
        response(json.dumps(valid_result())),
        response(json.dumps(valid_result())),
    ]
    result = provider.draft_equipment(request)
    assert result.specifications[0].name == "센서"
    assert client.responses.create.call_count == 2
    first_call, search_call = client.responses.create.call_args_list
    assert "tools" not in first_call.kwargs
    assert search_call.kwargs["tools"] == [
        {"type": "web_search", "search_context_size": "medium"}
    ]
    assert search_call.kwargs["include"] == ["web_search_call.action.sources"]
    assert search_call.kwargs["tool_choice"] == "required"
    assert search_call.kwargs["max_tool_calls"] == 1
    search_input = json.loads(search_call.kwargs["input"])
    assert search_input == {
        "category": "CAMERA",
        "name": "Sony Alpha 7 III",
        "manufacturer": "Sony",
        "modelName": "Alpha 7 III",
        "identificationStatus": "MODEL_LIKELY",
        "searchScope": "PRIMARY_EQUIPMENT_MODEL_ONLY",
    }
    assert "identificationEvidence" not in search_call.kwargs["input"]
    assert "visibleAccessories" not in search_call.kwargs["input"]
    assert "제조사 공식 제품·지원·매뉴얼 페이지를 우선" in search_call.kwargs[
        "instructions"
    ]
    enrichment_prompt = search_call.kwargs["instructions"]
    assert "공식 자료에서 확인한 사양과 특징을 중심으로 처음부터 다시 작성" in (
        enrichment_prompt
    )
    assert "핵심 specifications 3~5개" in enrichment_prompt
    assert "버튼·다이얼·단자·화면의 위치" in enrichment_prompt
    assert "함께 촬영되어 있습니다" in enrichment_prompt
    assert "등록자가 대여자에게 제품을 직접 소개" in enrichment_prompt
    assert "등록 사진 기준으로 눈에 띄는 파손" in enrichment_prompt
    assert "대여자에게 그대로 공개되는" in enrichment_prompt
    assert "제조사 모델명입니다." in enrichment_prompt
    assert "확인되지 않습니다" in enrichment_prompt
    assert "공백 포함 250~500자" in enrichment_prompt
    assert provider.usage == {
        "input_tokens": 200,
        "output_tokens": 100,
        "estimated_cost_micros": 400,
    }


def test_enrichment_rebuilds_description_when_specs_are_missing(configured):
    provider, client, request = configured
    provider.settings.openai_equipment_web_search = True
    visual = valid_result()
    enriched = valid_result()
    enriched["description"] = (
        "렌즈 전면에 초점 거리가 표기되어 있습니다. 카메라 본체는 검은색입니다. "
        "렌즈와 본체가 함께 촬영되어 있습니다."
    )
    enriched["specifications"] = [
        {"name": "센서", "value": "35mm 풀프레임 CMOS"},
        {"name": "유효 화소", "value": "약 2,420만 화소"},
        {"name": "손떨림 보정", "value": "바디 내장 5축"},
    ]
    client.responses.create.side_effect = [
        response(json.dumps(visual)),
        response(json.dumps(enriched)),
    ]

    result = provider.draft_equipment(request)

    assert result.description is not None
    assert result.description.startswith("Sony Alpha 7 III입니다.")
    assert "35mm 풀프레임 CMOS" in result.description
    assert "약 2,420만 화소" in result.description
    assert "센서 사양은 35mm 풀프레임 CMOS" in result.description
    assert "화상 처리 엔진은" not in result.description
    assert "카메라 대여입니다" not in result.description
    assert "렌즈 전면" not in result.description
    assert "검은색" not in result.description
    assert "함께 촬영" not in result.description
    assert "보입니다" not in result.description
    assert "확인되지 않습니다" not in result.description


def test_enrichment_keeps_natural_specification_focused_description(configured):
    provider, client, request = configured
    provider.settings.openai_equipment_web_search = True
    visual = valid_result()
    enriched = valid_result()
    enriched["description"] = (
        "Sony Alpha 7 III입니다. 35mm 풀프레임 CMOS 센서와 약 2,420만 화소를 "
        "지원해 고해상도 사진과 영상 촬영에 사용할 수 있습니다. "
        "바디 내장 5축 손떨림 보정 기능을 지원합니다."
    )
    enriched["specifications"] = [
        {"name": "센서", "value": "35mm 풀프레임 CMOS"},
        {"name": "유효 화소", "value": "약 2,420만 화소"},
        {"name": "손떨림 보정", "value": "바디 내장 5축"},
    ]
    client.responses.create.side_effect = [
        response(json.dumps(visual)),
        response(json.dumps(enriched)),
    ]

    result = provider.draft_equipment(request)

    assert result.description == enriched["description"]


def test_official_search_metadata_restores_missing_reference_sources(configured):
    provider, client, request = configured
    provider.settings.openai_equipment_web_search = True
    visual = valid_result()
    enriched = valid_result()
    enriched["referenceSources"] = []
    search_response = response(json.dumps(enriched))
    search_response.output = [
        SimpleNamespace(
            action=SimpleNamespace(
                sources=[
                    SimpleNamespace(
                        url="https://www.sony.com/electronics/support/alpha-7-iii"
                    ),
                    SimpleNamespace(url="https://www.sony.com/electronics/support/lens"),
                    SimpleNamespace(url="https://example.test/untrusted"),
                ]
            )
        )
    ]
    client.responses.create.side_effect = [
        response(json.dumps(visual)),
        search_response,
    ]

    result = provider.draft_equipment(request)

    assert [(source.title, source.url) for source in result.reference_sources] == [
        ("Sony 공식 자료", "https://www.sony.com/electronics/support/alpha-7-iii")
    ]
    assert result.specifications
    assert "35mm 풀프레임 CMOS" in result.description


def test_model_returned_accessory_source_is_removed(configured):
    provider, client, request = configured
    provider.settings.openai_equipment_web_search = True
    visual = valid_result()
    enriched = valid_result()
    enriched["referenceSources"] = [
        {
            "title": "Sony 렌즈 지원",
            "url": "https://www.sony.com/electronics/support/lens",
        },
        {
            "title": "Sony Alpha 7 III 지원",
            "url": "https://www.sony.com/electronics/support/alpha-7-iii",
        },
    ]
    client.responses.create.side_effect = [
        response(json.dumps(visual)),
        response(json.dumps(enriched)),
    ]

    result = provider.draft_equipment(request)

    assert [(source.title, source.url) for source in result.reference_sources] == [
        (
            "Sony Alpha 7 III 지원",
            "https://www.sony.com/electronics/support/alpha-7-iii",
        )
    ]


def test_official_brand_domain_is_allowed_for_console():
    assert OpenAiProvider._is_primary_official_source(
        "https://www.playstation.com/en-us/ps5/",
        "Sony",
        "PlayStation 5",
    )
    assert not OpenAiProvider._is_primary_official_source(
        "https://www.playstation.com/en-us/ps4/",
        "Sony",
        "PlayStation 5",
    )


def test_console_fallback_description_groups_related_specs(configured):
    provider, client, request = configured
    provider.settings.openai_equipment_web_search = True
    visual = valid_result()
    visual.update(
        {
            "category": "GAME_CONSOLE",
            "name": "Sony PlayStation 5",
            "manufacturer": "Sony",
            "modelName": "PlayStation 5",
        }
    )
    enriched = dict(visual)
    enriched["description"] = "흰색 본체와 컨트롤러가 함께 촬영되어 있습니다."
    enriched["specifications"] = [
        {"name": "CPU", "value": "AMD Zen 2 8코어"},
        {"name": "GPU", "value": "AMD RDNA 2 기반"},
        {"name": "시스템 메모리", "value": "GDDR6 16GB"},
        {"name": "스토리지", "value": "825GB SSD"},
    ]
    enriched["keyFeatures"] = ["DualSense 햅틱 피드백", "Tempest 3D 오디오"]
    enriched["referenceSources"] = [
        {
            "title": "PlayStation 5",
            "url": "https://www.playstation.com/en-us/ps5/",
        }
    ]
    client.responses.create.side_effect = [
        response(json.dumps(visual)),
        response(json.dumps(enriched)),
    ]

    result = provider.draft_equipment(request)

    assert result.description == (
        "Sony PlayStation 5입니다. CPU는 AMD Zen 2 8코어입니다. "
        "GPU는 AMD RDNA 2 기반입니다. 시스템 메모리는 GDDR6 16GB, "
        "내장 스토리지는 825GB SSD입니다. 지원 기능에는 DualSense 햅틱 피드백, "
        "Tempest 3D 오디오 등이 포함됩니다."
    )


def test_placeholder_identity_is_removed_without_search(configured):
    provider, client, request = configured
    provider.settings.openai_equipment_web_search = True
    payload = valid_result()
    payload.update(
        {
            "name": "제조사 모델명",
            "manufacturer": "제조사명",
            "modelName": "모델명",
            "identificationStatus": "MODEL_CONFIRMED",
            "identificationEvidence": ["모델 번호: 모델번호"],
            "description": "센서명과 화소수를 갖춘 카메라입니다.",
        }
    )
    client.responses.create.return_value = response(json.dumps(payload))
    result = provider.draft_equipment(request)
    assert result.name == "카메라"
    assert result.manufacturer is None
    assert result.model_name is None
    assert result.identification_status == "PRODUCT_TYPE_ONLY"
    assert result.identification_evidence == []
    assert result.description is None
    client.responses.create.assert_called_once()


def test_normal_model_confirmation_sentence_is_not_treated_as_placeholder(configured):
    provider, client, request = configured
    payload = valid_result()
    payload.update(
        {
            "manufacturer": None,
            "modelName": None,
            "name": "미러리스 카메라",
            "identificationStatus": "PRODUCT_TYPE_ONLY",
            "description": (
                "제조사 및 모델명은 사진에서 확인되지 않으며 "
                "외관을 직접 확인해야 합니다."
            ),
        }
    )
    client.responses.create.return_value = response(json.dumps(payload))
    result = provider.draft_equipment(request)
    assert result.description == payload["description"]


def test_enrichment_without_sources_falls_back_to_visual_description(configured):
    provider, client, request = configured
    provider.settings.openai_equipment_web_search = True
    visual = valid_result()
    visual["description"] = "사진에서 확인한 외관 상태입니다."
    enriched = valid_result()
    enriched["referenceSources"] = []
    enriched["description"] = "출처 없이 만든 사양 설명입니다."
    client.responses.create.side_effect = [
        response(json.dumps(visual)),
        response(json.dumps(enriched)),
    ]

    result = provider.draft_equipment(request)

    assert result.specifications == []
    assert result.key_features == []
    assert result.description == visual["description"]
