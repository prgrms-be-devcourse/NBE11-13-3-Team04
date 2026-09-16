package com.example.iter.ai

import com.example.iter.ai.client.AiServiceClient
import com.example.iter.ai.client.AiServiceException
import com.example.iter.ai.domain.repository.ConditionAnalysisJobRepository
import com.example.iter.ai.dto.AiJobAccepted
import com.example.iter.ai.dto.AiJobRequest
import com.example.iter.ai.dto.AiJobStatus
import com.example.iter.ai.dto.ConditionAnalysisRequest
import com.example.iter.ai.service.ConditionAnalysisService
import com.example.iter.ai.service.ConditionEvidenceImages
import com.example.iter.auth.domain.entity.User
import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.image.CaptureView
import com.example.iter.common.security.JwtTokenProvider
import com.example.iter.device.domain.entity.Equipment
import com.example.iter.device.domain.entity.EquipmentCategory
import com.example.iter.device.domain.repository.EquipmentRepository
import com.example.iter.reservation.domain.entity.ProductConditionType
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.repository.RentalRepository
import com.example.iter.reservation.dto.response.ConditionEvidenceImageResponse
import com.example.iter.reservation.dto.response.ConditionEvidenceResponse
import com.example.iter.reservation.dto.response.ReturnComparisonResponse
import com.example.iter.reservation.service.ReturnService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest(properties = ["app.ai.daily-condition-limit=1"])
@AutoConfigureMockMvc
class ConditionAnalysisApiTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var equipment: EquipmentRepository
    @Autowired lateinit var rentals: RentalRepository
    @Autowired lateinit var jobs: ConditionAnalysisJobRepository
    @Autowired lateinit var service: ConditionAnalysisService
    @Autowired lateinit var tokens: JwtTokenProvider
    @MockitoBean lateinit var client: AiServiceClient
    @MockitoBean lateinit var returns: ReturnService
    @MockitoBean lateinit var images: ConditionEvidenceImages

    private lateinit var owner: User
    private lateinit var renter: User
    private lateinit var item: Equipment
    private lateinit var rental: Rental

    @BeforeEach
    fun setup() {
        owner = user()
        renter = user()
        item = equipment.saveAndFlush(
            Equipment(
                requireNotNull(owner.id),
                EquipmentCategory.CAMERA,
                "테스트 카메라",
                null,
                BigDecimal.valueOf(1000)
            )
        )
        rental = rental(RentalStatus.RETURNED)
        val before = ConditionEvidenceResponse(ProductConditionType.NORMAL, null, evidence("before"), LocalDateTime.now())
        val after = ConditionEvidenceResponse(ProductConditionType.NORMAL, null, evidence("after"), LocalDateTime.now())
        whenever(returns.getReturnComparison(any(), any())).thenReturn(
            ReturnComparisonResponse(
                rental.id, "카메라", null, LocalDate.now(), LocalDate.now(), LocalDate.now(),
                evidence("listing"), before, after
            )
        )
        whenever(images.reference(any(), any(), any())).thenAnswer { call ->
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
            mapOf(
                "imageId" to call.getArgument<String>(1),
                "objectKey" to call.getArgument<String>(0),
                "etag" to "v1",
                "captureSlot" to call.getArgument<String>(2),
                "contentType" to "image/jpeg",
                "sizeBytes" to 100
            )
        }
        whenever(client.createJob(any())).thenAnswer { call ->
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
            val request = call.getArgument<AiJobRequest>(0)
            val sourceId = requireNotNull(requireNotNull(request.source).id).toLong()
            assertThat(jobs.findById(sourceId)).isPresent()
            AiJobAccepted(request.jobId, AiJobStatus.Status.PENDING, false)
        }
    }

    @Test
    fun 소유자만_동의후_분석하며_대여상태는_변경하지_않는다() {
        mvc.post(url(rental)) {
            header("Authorization", bearer(owner))
            contentType = MediaType.APPLICATION_JSON
            content = """{"externalAiConsent":true}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.status") { value("PENDING") }
        }
        verify(images).reference("before-front.jpg", "before-front", "FRONT")
        verify(images).reference("after-rear.jpg", "after-rear", "REAR")
        assertThat(rentals.findById(rental.id).orElseThrow().status).isEqualTo(RentalStatus.RETURNED)
        verify(returns, never()).confirmReturn(any(), any(), any())
    }

    @Test
    fun 비로그인과_대여자와_타인의_생성조회재접수를_차단한다() {
        mvc.get(url(rental)).andExpect { status { isUnauthorized() } }
        listOf(renter, user()).forEach { other ->
            mvc.get(url(rental)) { header("Authorization", bearer(other)) }
                .andExpect { status { isForbidden() } }
            mvc.post("${url(rental)}/retry") { header("Authorization", bearer(other)) }
                .andExpect { status { isForbidden() } }
            mvc.post(url(rental)) {
                header("Authorization", bearer(other))
                contentType = MediaType.APPLICATION_JSON
                content = """{"externalAiConsent":true}"""
            }.andExpect { status { isForbidden() } }
        }
        verifyNoInteractions(images, client)
    }

    @Test
    fun 외부_AI_동의가_없으면_차단한다() {
        listOf("{}", """{"externalAiConsent":false}""").forEach { body ->
            mvc.post(url(rental)) {
                header("Authorization", bearer(owner))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isBadRequest() } }
        }
        verifyNoInteractions(images, client)
    }

    @Test
    fun 접수불명확과_재접수에도_같은_UUID를_사용한다() {
        doThrow(AiServiceException(0)).whenever(client).createJob(any())
        val first = service.create(owner.id, rental.id, input())
        val jobId = requireNotNull(first.jobId)
        assertThat(first.status).isEqualTo("SUBMISSION_UNKNOWN")
        whenever(client.getJob(jobId)).thenThrow(AiServiceException(404))
        assertThat(service.get(owner.id, rental.id).status).isEqualTo("SUBMISSION_UNKNOWN")
        assertThat(service.retry(owner.id, rental.id).jobId).isEqualTo(first.jobId)
        assertThat(service.create(owner.id, rental.id, input()).jobId).isEqualTo(first.jobId)
        verify(client, times(3)).createJob(argThat { jobId == first.jobId })
        verify(images, times(9)).reference(any(), any(), any())
    }

    @Test
    fun 완료된_대여는_신규분석하지_않고_일일한도를_검사한다() {
        val closed = rental(RentalStatus.COMPLETED)
        assertThatThrownBy { service.create(owner.id, closed.id, input()) }.isInstanceOf(CustomException::class.java)
        service.create(owner.id, rental.id, input())
        val another = rental(RentalStatus.RETURNED)
        assertThatThrownBy { service.create(owner.id, another.id, input()) }
            .isInstanceOfSatisfying(CustomException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.AI_CONDITION_DAILY_LIMIT)
            }
        verify(client, times(1)).createJob(any())
    }

    @Test
    fun 미접수와_성공조회_장애를_구분한다() {
        mvc.get(url(rental)) { header("Authorization", bearer(owner)) }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NOT_REQUESTED") }
        }
        val created = service.create(owner.id, rental.id, input())
        val jobId = requireNotNull(created.jobId)
        whenever(client.getJob(jobId)).thenReturn(
            AiJobStatus(
                jobId, AiJobRequest.FeatureType.RETURN_CONDITION_V2, AiJobStatus.Status.SUCCEEDED,
                mapOf("assessment" to "INCONCLUSIVE"), null, "fake", "fake-v1", null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
            )
        )
        mvc.get(url(rental)) { header("Authorization", bearer(owner)) }.andExpect {
            status { isOk() }
            jsonPath("$.analysis.assessment") { value("INCONCLUSIVE") }
            jsonPath("$.comparedImageIds.before-front") { value("before-front") }
            jsonPath("$.comparedImageIds.after-rear") { value("after-rear") }
        }
        whenever(client.getJob(jobId)).thenThrow(AiServiceException(0))
        mvc.get(url(rental)) { header("Authorization", bearer(owner)) }
            .andExpect { status { isServiceUnavailable() } }
    }

    @Test
    fun 같은_대여의_동시요청도_한_UUID만_사용한다() {
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val first = executor.submit<UUID> { start.await(); service.create(owner.id, rental.id, input()).jobId }
            val second = executor.submit<UUID> { start.await(); service.create(owner.id, rental.id, input()).jobId }
            start.countDown()
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun 다른_대여의_동시요청도_일일한도를_넘지_않는다() {
        val another = rental(RentalStatus.RETURNED)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val first = executor.submit<String> { start.await(); tryCreate(rental) }
            val second = executor.submit<String> { start.await(); tryCreate(another) }
            start.countDown()
            assertThat(listOf(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("PENDING", "AI_CONDITION_DAILY_LIMIT")
        }
    }

    private fun tryCreate(target: Rental): String = try {
        requireNotNull(service.create(owner.id, target.id, input()).status)
    } catch (exception: CustomException) {
        exception.errorCode.name
    }

    private fun user(): User = users.saveAndFlush(
        User.builder().email("${UUID.randomUUID()}@example.test").name("테스트").password("test-only").build()
    )

    private fun rental(status: RentalStatus): Rental = rentals.saveAndFlush(
        Rental.builder().equipmentId(item.id).ownerIdSnapshot(owner.id).renterId(renter.id)
            .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1)).productNameSnapshot("카메라")
            .dailyPriceSnapshot(BigDecimal.valueOf(1000)).rentalDays(1).totalPrice(BigDecimal.valueOf(1000))
            .status(status).build()
    )

    private fun evidence(phase: String) = listOf(
        ConditionEvidenceImageResponse(CaptureView.FRONT, "$phase-front.jpg"),
        ConditionEvidenceImageResponse(CaptureView.SIDE, "$phase-side.jpg"),
        ConditionEvidenceImageResponse(CaptureView.REAR, "$phase-rear.jpg")
    )

    private fun input() = ConditionAnalysisRequest(true)
    private fun url(target: Rental) = "/api/v1/rentals/${target.id}/condition-analysis"
    private fun bearer(user: User) = "Bearer ${tokens.generateAccessToken(user.toAuthUser())}"
}
