package com.example.iter.composition.ai

import com.example.iter.auth.domain.entity.User
import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.ai.service.PreviousConditionAnalysisService
import com.example.iter.ai.service.ReportAnalysisImages
import com.example.iter.common.security.Role
import com.example.iter.common.security.UserStatus
import com.example.iter.delivery.domain.repository.ShippingRepository
import com.example.iter.device.domain.entity.Equipment
import com.example.iter.device.domain.entity.EquipmentCategory
import com.example.iter.device.domain.entity.EquipmentImage
import com.example.iter.device.domain.entity.EquipmentStatus
import com.example.iter.device.domain.entity.ProductConditionType
import com.example.iter.device.domain.repository.EquipmentImageRepository
import com.example.iter.device.domain.repository.EquipmentRepository
import com.example.iter.dispute.domain.entity.Report
import com.example.iter.dispute.domain.entity.ReportStatus
import com.example.iter.dispute.domain.entity.ReportTargetType
import com.example.iter.dispute.domain.repository.DisputeRepository
import com.example.iter.dispute.domain.repository.ReportRepository
import com.example.iter.payment.domain.repository.PaymentRepository
import com.example.iter.reservation.domain.entity.Receipt
import com.example.iter.reservation.domain.entity.ReceiptImage
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.ReturnReceipt
import com.example.iter.reservation.domain.entity.ReturnReceiptImage
import com.example.iter.reservation.domain.repository.ReceiptImageRepository
import com.example.iter.reservation.domain.repository.ReceiptRepository
import com.example.iter.reservation.domain.repository.RentalRepository
import com.example.iter.reservation.domain.repository.ReturnReceiptImageRepository
import com.example.iter.reservation.domain.repository.ReturnReceiptRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

class ReportAnalysisContextBuilderTest {
    private val reports: ReportRepository = mock()
    private val users: UserRepository = mock()
    private val equipment: EquipmentRepository = mock()
    private val equipmentImages: EquipmentImageRepository = mock()
    private val rentals: RentalRepository = mock()
    private val payments: PaymentRepository = mock()
    private val shipping: ShippingRepository = mock()
    private val receipts: ReceiptRepository = mock()
    private val receiptImages: ReceiptImageRepository = mock()
    private val returnReceipts: ReturnReceiptRepository = mock()
    private val returnReceiptImages: ReturnReceiptImageRepository = mock()
    private val disputes: DisputeRepository = mock()
    private val images: ReportAnalysisImages = mock()
    private val previousConditionAnalysis: PreviousConditionAnalysisService = mock()

    @Test
    fun 회원_신고는_거래_관련_문구가_있을_때만_최근_거래를_조회한다() {
        val report = Report.builder()
            .id(40L)
            .reporterId(2L)
            .targetType(ReportTargetType.USER)
            .targetId(3L)
            .reason("회원 신고")
            .status(ReportStatus.RECEIVED)
            .build()
        val target = User.builder()
            .id(3L)
            .email("private@example.test")
            .name("실명")
            .nickname("공개닉네임")
            .phone("010-0000-0000")
            .password("secret")
            .role(Role.USER)
            .status(UserStatus.ACTIVE)
            .build()
        whenever(reports.findById(40L)).thenReturn(Optional.of(report))
        whenever(users.findById(3L)).thenReturn(Optional.of(target))

        val nicknameContext = builder().build(40L, "닉네임에 욕설이 있습니다.")

        verify(rentals, never()).findLatestBetweenUsers(2L, 3L)
        assertThat(nicknameContext.publicContent).containsEntry("targetNickname", "공개닉네임")
        assertThat(nicknameContext.toString())
            .doesNotContain("private@example.test", "실명", "010-0000-0000", "secret")

        whenever(rentals.findLatestBetweenUsers(2L, 3L)).thenReturn(Optional.empty())
        builder().build(40L, "반납 과정에서 문제가 생겼습니다.")
        verify(rentals).findLatestBetweenUsers(2L, 3L)
    }

    @Test
    fun 기존_상태비교_결과가_있는_거래_신고는_사진을_다시_보내지_않는다() {
        val report = Report.builder()
            .id(50L)
            .reporterId(2L)
            .targetType(ReportTargetType.RENTAL)
            .targetId(20L)
            .reason("반납 상태 불일치")
            .status(ReportStatus.RECEIVED)
            .build()
        val rental = rental()
        val item = equipment()
        val listing = EquipmentImage(
            item,
            "https://example.test/listing.png",
            "equipment/public/10/listing.png",
            0,
            false,
            101L
        )
        val receipt = Receipt.builder().id(201L).rental(rental).conditionDetail("수령 상태").build()
        val returned = ReturnReceipt.builder().id(301L).rental(rental).conditionDetail("반납 시 긁힘").build()
        val receiptImage = ReceiptImage.builder()
            .id(202L)
            .receipt(receipt)
            .imageUrl("equipment/private/rental-evidence/20/2/receipt/a.png")
            .build()
        val returnImage = ReturnReceiptImage.builder()
            .id(302L)
            .returnReceipt(returned)
            .imageUrl("equipment/private/rental-evidence/20/2/return/b.png")
            .build()

        whenever(reports.findById(50L)).thenReturn(Optional.of(report))
        whenever(rentals.findById(20L)).thenReturn(Optional.of(rental))
        whenever(equipment.findById(10L)).thenReturn(Optional.of(item))
        whenever(equipmentImages.findByEquipmentIdOrderBySortOrderAscIdAsc(10L)).thenReturn(listOf(listing))
        whenever(receipts.findByRentalId(20L)).thenReturn(Optional.of(receipt))
        whenever(receiptImages.findByReceipt_IdOrderBySortOrderAscIdAsc(201L)).thenReturn(listOf(receiptImage))
        whenever(returnReceipts.findByRentalId(20L)).thenReturn(Optional.of(returned))
        whenever(returnReceiptImages.findByReturnReceipt_IdOrderBySortOrderAscIdAsc(301L)).thenReturn(listOf(returnImage))
        whenever(images.reference(any(), any(), any())).thenAnswer { call ->
            Optional.of(
                mapOf(
                    "imageId" to call.getArgument<String>(1),
                    "captureSlot" to call.getArgument<String>(2)
                )
            )
        }
        whenever(previousConditionAnalysis.findSucceededResult(20L)).thenReturn(
            Optional.of(
                mapOf(
                    "sourceFeature" to "RETURN_CONDITION_V1",
                    "assessment" to "CHANGE_SUSPECTED",
                    "summary" to "반납 사진에서 긁힘 후보가 보입니다."
                )
            )
        )

        val context = builder().build(50L, "반납 과정에서 긁힘이 생겼습니다.")

        assertThat(context.systemFacts)
            .containsEntry("rentalRelation", "DIRECT_REPORT_TARGET")
            .containsEntry("rentalStatus", "RETURNED")
        assertThat(context.publicContent)
            .containsEntry("receiptConditionDetail", "수령 상태")
            .containsEntry("returnConditionDetail", "반납 시 긁힘")
        assertThat(context.publicContent["equipmentDescription"])
            .contains("[이메일 제거]", "[전화번호 제거]")
        assertThat(context.evidenceImages).isEmpty()
        verify(images, never()).reference(any(), any(), any())
        assertThat(context.priorConditionAnalysis)
            .containsEntry("assessment", "CHANGE_SUSPECTED")
            .containsEntry("summary", "반납 사진에서 긁힘 후보가 보입니다.")
        assertThat(context.toString())
            .doesNotContain("contact@example.test", "010-1234-5678", "receiver", "address", "phone")

        clearInvocations(images)
        whenever(previousConditionAnalysis.findSucceededResult(20L)).thenReturn(Optional.empty())
        val fallback = builder().build(50L, "반납 과정에서 긁힘이 생겼습니다.")
        assertThat(fallback.evidenceImages.map { it["captureSlot"] })
            .containsExactly("LISTING_1", "RECEIPT_1", "RETURN_1")
    }

    private fun rental() = Rental.builder()
        .id(20L)
        .equipmentId(10L)
        .ownerIdSnapshot(1L)
        .renterId(2L)
        .startDate(LocalDate.of(2026, 9, 1))
        .endDate(LocalDate.of(2026, 9, 5))
        .productNameSnapshot("카메라")
        .categorySnapshot("CAMERA")
        .dailyPriceSnapshot(BigDecimal.valueOf(30_000))
        .rentalDays(5)
        .totalPrice(BigDecimal.valueOf(150_000))
        .status(RentalStatus.RETURNED)
        .build()

    private fun equipment() = Equipment(
        ownerId = 1L,
        category = EquipmentCategory.CAMERA,
        name = "Canon 카메라",
        description = "등록 설명 contact@example.test 010-1234-5678",
        dailyPrice = BigDecimal.valueOf(30_000),
        status = EquipmentStatus.ACTIVE,
        productCondition = ProductConditionType.NORMAL,
        id = 10L
    )

    private fun builder(): ReportAnalysisContextBuilder {
        val sanitizer = ReportTextSanitizer()
        val evidenceCollector = ReportEvidenceCollector(
            equipmentImages,
            receiptImages,
            returnReceiptImages,
            images
        )
        val rentalContributor = RentalReportContextContributor(
            rentals,
            equipment,
            payments,
            shipping,
            receipts,
            returnReceipts,
            disputes,
            previousConditionAnalysis,
            evidenceCollector,
            sanitizer
        )
        val userContributor = UserReportContextContributor(
            users,
            reports,
            rentals,
            rentalContributor,
            sanitizer,
            Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneId.of("Asia/Seoul"))
        )
        val equipmentContributor = EquipmentReportContextContributor(
            equipment,
            reports,
            rentals,
            rentalContributor,
            evidenceCollector,
            sanitizer
        )
        val snapshotLoader = ReportAnalysisSnapshotLoader(
            reports,
            listOf(userContributor, equipmentContributor, rentalContributor)
        )
        return ReportAnalysisContextBuilder(snapshotLoader, evidenceCollector)
    }
}
