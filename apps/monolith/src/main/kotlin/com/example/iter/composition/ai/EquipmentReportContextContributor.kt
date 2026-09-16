package com.example.iter.composition.ai

import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.device.domain.repository.EquipmentRepository
import com.example.iter.dispute.domain.entity.Report
import com.example.iter.dispute.domain.entity.ReportTargetType
import com.example.iter.dispute.domain.repository.ReportRepository
import com.example.iter.reservation.domain.repository.RentalRepository
import org.springframework.stereotype.Component

@Component
class EquipmentReportContextContributor(
    private val equipment: EquipmentRepository,
    private val reports: ReportRepository,
    private val rentals: RentalRepository,
    private val rentalContributor: RentalReportContextContributor,
    private val evidenceCollector: ReportEvidenceCollector,
    private val sanitizer: ReportTextSanitizer
) : ReportTargetContextContributor {
    override val targetType: ReportTargetType = ReportTargetType.EQUIPMENT

    // 장비의 현재 상태·신고 횟수·공개 설명과 신고자의 최근 관련 거래를 함께 구성합니다.
    override fun contribute(report: Report, reviewedDescription: String?, draft: ReportAnalysisDraft) {
        val target = equipment.findById(report.targetId).orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

        draft.addFact("equipmentCategory", target.category)
        draft.addFact("equipmentStatus", target.status)
        draft.addFact("listedCondition", target.productCondition)
        draft.addFact("dailyPrice", target.dailyPrice)
        draft.addFact(
            "reportsAgainstEquipment",
            reports.countByTargetTypeAndTargetId(ReportTargetType.EQUIPMENT, target.id)
        )
        sanitizer.addPublicContent(draft.publicContent, "equipmentName", target.name)
        sanitizer.addPublicContent(draft.publicContent, "equipmentDescription", target.description)
        sanitizer.addPublicContent(draft.publicContent, "equipmentConditionDetail", target.conditionDetail)
        evidenceCollector.collectEquipmentImages(target.id, draft, 2)

        // 신고자가 해당 장비를 실제로 빌린 적이 있으면 가장 최근 거래 증빙까지 연결합니다.
        rentals.findFirstByEquipmentIdAndRenterIdOrderByCreatedAtDesc(target.id, report.reporterId).ifPresent { rental ->
            rentalContributor.addRentalContext(
                rental,
                "LATEST_RENTAL_FOR_REPORTED_EQUIPMENT",
                false,
                draft
            )
        }
    }
}
