from app.domain.contracts import FeatureType
from app.providers.fake_provider import FakeAiProvider
from app.runtime.pipeline_registry import run_pipeline


def test_fake_report_pipeline_returns_human_review() -> None:
    result = run_pipeline(
        FeatureType.REPORT_TRIAGE_V1,
        {
            "reason": "장비 상태가 다름",
            "description": "반납된 장비에 새로운 흠집이 있다고 주장합니다.",
            "targetContext": {"rental": {"status": "RETURNED"}},
        },
        FakeAiProvider(),
    )

    assert result["processingDisposition"] == "HUMAN_REVIEW_REQUIRED"
    assert result["priority"] == "NORMAL"
    assert "sanction" not in result


def test_fake_equipment_pipeline_never_generates_price() -> None:
    result = run_pipeline(
        FeatureType.EQUIPMENT_DRAFT_V1,
        {
            "images": [
                {
                    "imageId": "image-0000000001",
                    "objectKey": "equipment/private/image-1.jpg",
                    "captureSlot": "OVERVIEW",
                    "sha256": "a" * 64,
                    "contentType": "image/jpeg",
                    "sizeBytes": 1024,
                }
            ],
            "hints": {"name": "VR 세트", "category": "VR"},
        },
        FakeAiProvider(),
    )

    assert result["name"] == "VR 세트"
    assert "priceSuggestion" not in result
