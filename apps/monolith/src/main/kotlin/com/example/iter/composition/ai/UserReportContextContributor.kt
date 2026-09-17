package com.example.iter.composition.ai

import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.dispute.domain.entity.Report
import com.example.iter.dispute.domain.entity.ReportTargetType
import com.example.iter.dispute.domain.repository.ReportRepository
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.repository.RentalRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.util.EnumSet

@Component
class UserReportContextContributor(
    private val users: UserRepository,
    private val reports: ReportRepository,
    private val rentals: RentalRepository,
    private val rentalContributor: RentalReportContextContributor,
    private val sanitizer: ReportTextSanitizer,
    private val clock: Clock
) : ReportTargetContextContributor {
    override val targetType: ReportTargetType = ReportTargetType.USER

    // 대상 회원의 이용 상태와 완료·연체 통계를 수집하되 공개 가능한 닉네임만 AI에 전달합니다.
    override fun contribute(report: Report, reviewedDescription: String?, draft: ReportAnalysisDraft) {
        val target = users.findById(report.targetId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        val completed = EnumSet.of(RentalStatus.COMPLETED)
        val overdueStates = EnumSet.of(
            RentalStatus.RENTING,
            RentalStatus.RETURN_REQUESTED,
            RentalStatus.RETURNING,
            RentalStatus.RETURNED,
            RentalStatus.DISPUTED
        )

        draft.addFact("userStatus", target.status)
        draft.addFact("userRole", target.role)
        draft.addFact(
            "reportsAgainstUser",
            reports.countByTargetTypeAndTargetId(ReportTargetType.USER, target.id!!)
        )
        draft.addFact("completedRentalsAsRenter", rentals.countByRenterIdAndStatusIn(target.id!!, completed))
        draft.addFact("completedRentalsAsOwner", rentals.countByOwnerIdSnapshotAndStatusIn(target.id!!, completed))
        draft.addFact(
            "overdueRentalsAsRenter",
            rentals.countByRenterIdAndEndDateBeforeAndStatusIn(target.id!!, LocalDate.now(clock), overdueStates)
        )
        sanitizer.addPublicContent(draft.publicContent, "targetNickname", target.nickname)

        // 거래와 무관한 회원 신고에는 불필요한 거래·사진 정보를 전달하지 않습니다.
        if (sanitizer.mentionsTransaction(reviewedDescription)) {
            rentals.findLatestBetweenUsers(report.reporterId, target.id!!).ifPresent { rental ->
                rentalContributor.addRentalContext(
                    rental,
                    "LATEST_RENTAL_BETWEEN_RELATED_USERS",
                    true,
                    draft
                )
            }
        }
    }
}
