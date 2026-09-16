from __future__ import annotations

from typing import Protocol

from app.features.condition.schemas import ConditionInput, ConditionResult, ConditionResultV2
from app.features.equipment.schemas import EquipmentDraftResult, EquipmentInput
from app.features.reports.schemas import ReportInput, ReportResult


class AiProvider(Protocol):
    def compare_condition(self, request: ConditionInput) -> ConditionResult | ConditionResultV2: ...

    def triage_report(self, request: ReportInput) -> ReportResult: ...

    def draft_equipment(self, request: EquipmentInput) -> EquipmentDraftResult: ...
