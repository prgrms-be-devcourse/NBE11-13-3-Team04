package com.example.iter.ai

import com.example.iter.ai.client.AiServiceClient
import com.example.iter.ai.client.AiServiceException
import com.example.iter.ai.domain.repository.ReportAnalysisJobRepository
import com.example.iter.ai.dto.AiJobAccepted
import com.example.iter.ai.dto.AiJobRequest
import com.example.iter.ai.dto.AiJobStatus
import com.example.iter.ai.dto.ReportAnalysisRequest
import com.example.iter.ai.service.ReportAnalysisService
import com.example.iter.auth.domain.entity.User
import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.JwtTokenProvider
import com.example.iter.common.security.Role
import com.example.iter.common.security.UserStatus
import com.example.iter.dispute.domain.entity.Report
import com.example.iter.dispute.domain.entity.ReportStatus
import com.example.iter.dispute.domain.entity.ReportTargetType
import com.example.iter.dispute.domain.repository.ReportRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
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
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest(properties = ["app.ai.daily-report-limit=2"])
@AutoConfigureMockMvc
class ReportAnalysisApiTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var reports: ReportRepository
    @Autowired lateinit var jobs: ReportAnalysisJobRepository
    @Autowired lateinit var service: ReportAnalysisService
    @Autowired lateinit var tokens: JwtTokenProvider
    @MockitoBean lateinit var client: AiServiceClient
    private lateinit var admin: User
    private lateinit var report: Report

    @BeforeEach
    fun setup() {
        admin = user(Role.ADMIN)
        report = report(ReportStatus.RECEIVED)
        whenever(client.createJob(any())).thenAnswer { call ->
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
            val request = call.getArgument<AiJobRequest>(0)
            val sourceId = requireNotNull(requireNotNull(request.source).id).toLong()
            assertThat(jobs.findById(sourceId)).isPresent()
            AiJobAccepted(request.jobId, AiJobStatus.Status.PENDING, false)
        }
    }

    @AfterEach
    fun cleanup() {
        jobs.deleteAllInBatch()
        reports.deleteAllInBatch()
    }

    @Test
    fun 관리자만_접수와_조회가_가능하다() {
        mvc.get(url(report)).andExpect { status { isUnauthorized() } }
        mvc.post(url(report)) { contentType = MediaType.APPLICATION_JSON; content = body(true) }
            .andExpect { status { isUnauthorized() } }
        val normal = user(Role.USER)
        mvc.get(url(report)) { header("Authorization", bearer(normal)) }.andExpect { status { isForbidden() } }
        mvc.post(url(report)) { header("Authorization", bearer(normal)); contentType = MediaType.APPLICATION_JSON; content = body(true) }
            .andExpect { status { isForbidden() } }
        mvc.post("${url(report)}/retry").andExpect { status { isUnauthorized() } }
        mvc.post("${url(report)}/retry") { header("Authorization", bearer(normal)) }.andExpect { status { isForbidden() } }
        verifyNoInteractions(client)
    }

    @Test
    fun 정지된_관리자의_조회와_재접수도_차단한다() {
        admin.suspend(); users.saveAndFlush(admin)
        mvc.get(url(report)) { header("Authorization", bearer(admin)) }.andExpect { status { isForbidden() } }
        mvc.post("${url(report)}/retry") { header("Authorization", bearer(admin)) }.andExpect { status { isForbidden() } }
        verifyNoInteractions(client)
    }

    @Test
    fun 재접수는_본문없이_기존_입력을_재사용하고_미접수는_거부한다() {
        mvc.post("${url(report)}/retry") { header("Authorization", bearer(admin)) }.andExpect { status { isNotFound() } }
        val created = service.create(admin.id, reportId(report), input())
        mvc.post("${url(report)}/retry") { header("Authorization", bearer(admin)) }.andExpect {
            status { isAccepted() }; jsonPath("$.jobId") { value(created.jobId.toString()) }
        }
        verify(client, times(2)).createJob(argThat { jobId == created.jobId })
    }

    @Test
    fun 동의와_입력_검증_이전에_외부로_보내지_않는다() {
        mvc.post(url(report)) { header("Authorization", bearer(admin)); contentType = MediaType.APPLICATION_JSON; content = body(false) }
            .andExpect { status { isBadRequest() } }
        mvc.post(url(report)) {
            header("Authorization", bearer(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"description":"","externalAiConsent":true}"""
        }.andExpect { status { isBadRequest() } }
        verifyNoInteractions(client)
    }

    @Test
    fun 원본_개인정보를_전달하지_않고_수동_처리_필드를_변경하지_않는다() {
        mvc.post(url(report)) { header("Authorization", bearer(admin)); contentType = MediaType.APPLICATION_JSON; content = body(true) }
            .andExpect { status { isAccepted() }; jsonPath("$.status") { value("PENDING") } }
        verify(client).createJob(argThat {
            payload?.get("description") == "개인정보 제거한 검토 내용" &&
                payload?.get("reason") == "관리자 신고 검토 요청" &&
                safeUserContext(payload?.get("targetContext")) && payload?.get("evidenceImages") == emptyList<Any>()
        })
        val saved = reports.findById(reportId(report)).orElseThrow()
        assertThat(saved.status).isEqualTo(ReportStatus.RECEIVED)
        assertThat(saved.adminMemo).isNull()
        assertThat(saved.description).contains("private@example.test")
        assertThat(jobs.findById(reportId(report)).orElseThrow().requestJson).doesNotContain("private@example.test")
    }

    @Test
    fun 접수하지_않은_신고와_없는_신고를_구분한다() {
        mvc.get(url(report)) { header("Authorization", bearer(admin)) }.andExpect {
            status { isOk() }; jsonPath("$.status") { value("NOT_REQUESTED") }
        }
        mvc.get("/api/v1/admin/reports/9223372036854775807/ai-analysis") { header("Authorization", bearer(admin)) }
            .andExpect { status { isNotFound() } }
        verifyNoInteractions(client)
    }

    @Test
    fun 접수_불명확시_같은_UUID와_최초_입력을_보존한다() {
        doThrow(AiServiceException(0)).whenever(client).createJob(any())
        val first = service.create(admin.id, reportId(report), input())
        val jobId = requireNotNull(first.jobId)
        assertThat(first.status).isEqualTo("SUBMISSION_UNKNOWN")
        whenever(client.getJob(jobId)).thenThrow(AiServiceException(404))
        assertThat(service.get(reportId(report)).status).isEqualTo("SUBMISSION_UNKNOWN")
        doReturn(AiJobAccepted(jobId, AiJobStatus.Status.FAILED, true)).whenever(client).createJob(any())
        val again = service.create(admin.id, reportId(report), ReportAnalysisRequest("변경한 내용", true))
        assertThat(again.status).isEqualTo("FAILED"); assertThat(again.jobId).isEqualTo(jobId)
        verify(client, times(2)).createJob(argThat { this.jobId == jobId && payload?.get("description") == input().description })
        assertThat(jobs.countByAdminIdAndCreatedAtGreaterThanEqual(admin.id, LocalDateTime.now().minusDays(1))).isEqualTo(1)
    }

    @Test
    fun 처리_완료_신고는_새로_분석하지_않는다() {
        val closed = report(ReportStatus.RESOLVED)
        assertError(ErrorCode.AI_REPORT_CLOSED) { service.create(admin.id, reportId(closed), input()) }
        verifyNoInteractions(client)
    }

    @Test
    fun 동시에_접수해도_신고별_UUID는_하나다() {
        val otherAdmin = user(Role.ADMIN); val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val first = executor.submit<UUID?> { start.await(); service.create(admin.id, reportId(report), input()).jobId }
            val second = executor.submit<UUID?> { start.await(); service.create(otherAdmin.id, reportId(report), input()).jobId }
            start.countDown(); assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun 동시_요청도_관리자별_일일_한도를_지킨다() {
        service.create(admin.id, reportId(report), input())
        val firstReport = report(ReportStatus.RECEIVED); val secondReport = report(ReportStatus.RECEIVED)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val first = executor.submit<String> { start.await(); tryCreate(firstReport) }
            val second = executor.submit<String> { start.await(); tryCreate(secondReport) }
            start.countDown()
            assertThat(listOf(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("PENDING", "AI_REPORT_DAILY_LIMIT")
        }
    }

    @Test
    fun 성공_결과를_조회하며_장애와_다른_기능의_결과는_차단한다() {
        val created = service.create(admin.id, reportId(report), input()); val jobId = requireNotNull(created.jobId)
        whenever(client.getJob(jobId)).thenReturn(status(jobId, AiJobRequest.FeatureType.REPORT_TRIAGE_V1, mapOf("summary" to "검토 자료")))
        mvc.get(url(report)) { header("Authorization", bearer(admin)) }.andExpect {
            status { isOk() }; jsonPath("$.analysis.summary") { value("검토 자료") }
        }
        whenever(client.getJob(jobId)).thenThrow(AiServiceException(0))
        mvc.get(url(report)) { header("Authorization", bearer(admin)) }.andExpect { status { isServiceUnavailable() } }
        doReturn(status(jobId, AiJobRequest.FeatureType.EQUIPMENT_DRAFT_V1, emptyMap())).whenever(client).getJob(jobId)
        mvc.get(url(report)) { header("Authorization", bearer(admin)) }.andExpect { status { isServiceUnavailable() } }
    }

    private fun status(jobId: UUID, feature: AiJobRequest.FeatureType, result: Map<String, Any>) = AiJobStatus(
        jobId, feature, AiJobStatus.Status.SUCCEEDED, result, null, "fake", "fake-v1", null, null, null,
        LocalDateTime.now(), LocalDateTime.now()
    )
    private fun tryCreate(target: Report): String = try { requireNotNull(service.create(admin.id, reportId(target), input()).status) }
    catch (exception: CustomException) { exception.errorCode.name }
    private fun user(role: Role) = users.saveAndFlush(User.builder().email("${UUID.randomUUID()}@example.test")
        .name("테스트").password("test-only").role(role).status(UserStatus.ACTIVE).build())
    private fun report(status: ReportStatus) = reports.saveAndFlush(
        Report.builder()
            .reporterId(admin.id)
            .targetType(ReportTargetType.USER)
            .targetId(admin.id)
            .reason("신고 사유")
            .description("원본 private@example.test")
            .status(status)
            .build()
    )
    private fun input() = ReportAnalysisRequest("개인정보 제거한 검토 내용", true)
    private fun body(consent: Boolean) = """{"description":"개인정보 제거한 검토 내용","externalAiConsent":$consent}"""
    private fun url(target: Report) = "/api/v1/admin/reports/${target.id}/ai-analysis"
    private fun reportId(target: Report) = requireNotNull(target.id)
    private fun bearer(user: User) = "Bearer ${tokens.generateAccessToken(user.toAuthUser())}"
    private fun assertError(code: ErrorCode, block: () -> Unit) = assertThatThrownBy(block)
        .isInstanceOfSatisfying(CustomException::class.java) { assertThat(it.errorCode).isEqualTo(code) }

    @Suppress("UNCHECKED_CAST")
    private fun safeUserContext(value: Any?): Boolean {
        val context = value as? Map<String, Any> ?: return false
        val facts = context["systemFacts"] as? Map<String, String> ?: return false
        return context["targetType"] == "USER" && context["reportStatus"] == "RECEIVED" &&
            facts.containsKey("userStatus") && facts.containsKey("reportsAgainstUser") &&
            !context.toString().contains("private@example.test") && !context.toString().contains("email") &&
            !context.toString().contains("phone")
    }
}
