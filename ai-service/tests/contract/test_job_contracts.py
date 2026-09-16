import json
from pathlib import Path

import pytest

from app.domain.contracts import FeatureType, JobRequest

FIXTURE_DIR = Path(__file__).parent / "fixtures"


@pytest.mark.parametrize(
    ("file_name", "feature_type"),
    [
        ("return-condition-job.json", FeatureType.RETURN_CONDITION_V1),
        ("report-triage-job.json", FeatureType.REPORT_TRIAGE_V1),
        ("equipment-draft-job.json", FeatureType.EQUIPMENT_DRAFT_V1),
    ],
)
def test_job_fixture_matches_pydantic_contract(file_name: str, feature_type: FeatureType) -> None:
    raw = json.loads((FIXTURE_DIR / file_name).read_text(encoding="utf-8"))

    parsed = JobRequest.model_validate(raw)

    assert parsed.feature_type == feature_type
    assert parsed.model_dump(mode="json", by_alias=True)["featureType"] == feature_type.value


def test_report_description_over_limit_is_rejected() -> None:
    raw = json.loads((FIXTURE_DIR / "report-triage-job.json").read_text(encoding="utf-8"))
    raw["payload"]["description"] = "가" * 2001

    with pytest.raises(ValueError):
        JobRequest.model_validate(raw)


@pytest.mark.parametrize("field", ["beforeImages", "afterImages"])
def test_condition_accepts_only_one_selected_pair(field: str) -> None:
    raw = json.loads((FIXTURE_DIR / "return-condition-job.json").read_text(encoding="utf-8"))
    raw["payload"][field].append(raw["payload"][field][0])
    with pytest.raises(ValueError):
        JobRequest.model_validate(raw)


@pytest.mark.parametrize("job_id", ["job/abcdefghijklmnop", "abcdefghijklmnop", "x" * 36])
def test_invalid_job_id_is_rejected(job_id: str) -> None:
    raw = json.loads((FIXTURE_DIR / "report-triage-job.json").read_text(encoding="utf-8"))
    raw["jobId"] = job_id
    with pytest.raises(ValueError):
        JobRequest.model_validate(raw)
