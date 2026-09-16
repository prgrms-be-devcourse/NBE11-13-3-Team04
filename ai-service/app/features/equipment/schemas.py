from typing import Literal

from pydantic import Field

from app.domain.contracts import ContractModel, EquipmentPayload
from app.features.condition.schemas import EquipmentCondition

EquipmentInput = EquipmentPayload


# 모델 식별 뒤 공식 자료에서 확인한 사양 한 항목을 담는 클래스이다.
class EquipmentSpecification(ContractModel):
    name: str = Field(min_length=1, max_length=80)
    value: str = Field(min_length=1, max_length=200)


# 모델 식별과 사양 확인에 참고한 제조사 또는 신뢰할 수 있는 자료를 담는 클래스이다.
class EquipmentReferenceSource(ContractModel):
    title: str = Field(min_length=1, max_length=200)
    url: str = Field(min_length=1, max_length=2048, pattern=r"^https://")


# 사진을 분석해 제안한 장비 등록 폼의 초안과 직접 확인할 내용을 담는 클래스이다.
class EquipmentDraftResult(ContractModel):
    category: (
        Literal[
            "LAPTOP",
            "TABLET",
            "CAMERA",
            "LENS",
            "MONITOR",
            "VR",
            "GAME_CONSOLE",
            "PROJECTOR",
            "OTHER",
        ]
        | None
    )
    name: str | None = Field(max_length=100)
    manufacturer: str | None = Field(max_length=100)
    model_name: str | None = Field(max_length=150)
    identification_status: Literal[
        "MODEL_CONFIRMED",
        "MODEL_LIKELY",
        "PRODUCT_TYPE_ONLY",
        "UNKNOWN",
    ]
    identification_evidence: list[str] = Field(max_length=6)
    specifications: list[EquipmentSpecification] = Field(max_length=8)
    key_features: list[str] = Field(max_length=6)
    reference_sources: list[EquipmentReferenceSource] = Field(max_length=3)
    description: str | None = Field(max_length=2000)
    suggested_condition: EquipmentCondition | None
    condition_detail: str | None = Field(max_length=500)
    visible_accessories: list[str] = Field(max_length=20)
    uncertainties: list[str] = Field(max_length=20)
