package com.example.iter.ai

import com.example.iter.ai.client.AiServiceClient
import com.example.iter.ai.client.AiServiceException
import com.example.iter.ai.domain.repository.EquipmentDraftJobRepository
import com.example.iter.ai.dto.AiJobAccepted
import com.example.iter.ai.dto.AiJobRequest
import com.example.iter.ai.dto.AiJobStatus
import com.example.iter.ai.dto.EquipmentDraftRequest
import com.example.iter.ai.service.EquipmentDraftService
import com.example.iter.auth.domain.entity.User
import com.example.iter.common.security.UserStatus
import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.JwtTokenProvider
import com.example.iter.device.domain.entity.EquipmentImageUpload
import com.example.iter.device.domain.repository.EquipmentImageUploadRepository
import com.example.iter.device.storage.EquipmentImageStorage
import com.example.iter.device.storage.ValidatedUpload
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
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
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest(properties = ["app.ai.daily-draft-limit=2"])
@AutoConfigureMockMvc
class EquipmentDraftApiTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var uploads: EquipmentImageUploadRepository
    @Autowired lateinit var jobs: EquipmentDraftJobRepository
    @Autowired lateinit var service: EquipmentDraftService
    @Autowired lateinit var tokens: JwtTokenProvider
    @MockitoBean lateinit var client: AiServiceClient
    @MockitoBean lateinit var images: EquipmentImageStorage
    private lateinit var owner: User
    private lateinit var upload: EquipmentImageUpload

    @BeforeEach
    fun setup() {
        owner = user(UserStatus.ACTIVE)
        upload = uploads.saveAndFlush(
            EquipmentImageUpload.builder().userId(owner.id)
                .objectKey("equipment/temp/${owner.id}/${UUID.randomUUID()}.jpg")
                .expectedContentType("image/jpeg").expectedSize(123)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).build()
        )
        whenever(images.validateTemporaryUpload(any(), any(), any())).thenAnswer { call ->
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
            ValidatedUpload(call.getArgument(0), call.getArgument(1), call.getArgument(2), "etag")
        }
        whenever(client.createJob(any())).thenAnswer { call ->
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
            val request = call.getArgument<AiJobRequest>(0)
            assertThat(jobs.findByJobIdAndOwnerId(request.jobId.toString(), owner.id)).isPresent()
            AiJobAccepted(request.jobId, AiJobStatus.Status.PENDING, false)
        }
    }

    @Test
    fun 생성은_본인_사진만_참조하고_사진을_소비하지_않는다() {
        mvc.post(URL) { header("Authorization", bearer(owner)); contentType = MediaType.APPLICATION_JSON; content = body(upload.objectKey) }
            .andExpect { status { isAccepted() }; jsonPath("$.status") { value("PENDING") } }
        assertThat(uploads.findById(upload.id).orElseThrow().isUsed).isFalse()
        verify(images, never()).promote(any(), any())
    }

    @Test
    fun 다른_회원은_결과_조회와_재접수가_불가능하다() {
        val response = service.create(owner.id, request())
        val other = user(UserStatus.ACTIVE)
        clearInvocations(client)
        mvc.get("$URL/${response.jobId}") { header("Authorization", bearer(other)) }.andExpect { status { isNotFound() } }
        mvc.post("$URL/${response.jobId}/retry") { header("Authorization", bearer(other)) }.andExpect { status { isNotFound() } }
        verifyNoInteractions(client)
    }

    @Test
    fun 로그인하지_않은_호출은_차단한다() {
        mvc.post(URL) { contentType = MediaType.APPLICATION_JSON; content = body(upload.objectKey) }.andExpect { status { isUnauthorized() } }
        mvc.get("$URL/${UUID.randomUUID()}").andExpect { status { isUnauthorized() } }
        verifyNoInteractions(client)
    }

    @Test
    fun 다른_회원의_사진은_외부로_보내지_않는다() {
        mvc.post(URL) {
            header("Authorization", bearer(user(UserStatus.ACTIVE)))
            contentType = MediaType.APPLICATION_JSON
            content = body(upload.objectKey)
        }.andExpect { status { isNotFound() } }
        verifyNoInteractions(images, client)
    }

    @Test
    fun 만료되거나_사용한_사진과_중복_키를_차단한다() {
        upload.use(LocalDateTime.now()); uploads.saveAndFlush(upload)
        assertError(ErrorCode.IMAGE_UPLOAD_ALREADY_USED) { service.create(owner.id, request()) }
        val expired = uploads.saveAndFlush(
            EquipmentImageUpload.builder().userId(owner.id).objectKey("equipment/temp/expired-${UUID.randomUUID()}")
                .expectedContentType("image/jpeg").expectedSize(123).expiresAt(LocalDateTime.now().minusMinutes(1)).build()
        )
        assertError(ErrorCode.IMAGE_UPLOAD_EXPIRED) {
            service.create(owner.id, EquipmentDraftRequest(listOf(expired.objectKey), null, null))
        }
        assertThatThrownBy {
            service.create(owner.id, EquipmentDraftRequest(listOf(upload.objectKey, upload.objectKey), null, null))
        }.isInstanceOf(CustomException::class.java)
        verifyNoInteractions(images, client)
    }

    @Test
    fun 사진_없는_요청은_검증에서_차단한다() {
        mvc.post(URL) {
            header("Authorization", bearer(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"imageKeys":[]}"""
        }.andExpect { status { isBadRequest() } }
        verifyNoInteractions(client)
    }

    @Test
    fun 접수_실패도_UUID를_보존하고_재접수는_추가_횟수를_차감하지_않는다() {
        doThrow(AiServiceException(0)).whenever(client).createJob(any())
        val response = service.create(owner.id, request())
        val jobId = requireNotNull(response.jobId)
        assertThat(response.status).isEqualTo("SUBMISSION_UNKNOWN")
        whenever(client.getJob(jobId)).thenThrow(AiServiceException(404))
        doReturn(AiJobAccepted(jobId, AiJobStatus.Status.PENDING, false)).whenever(client).createJob(any())
        assertThat(service.retry(owner.id, jobId).jobId).isEqualTo(jobId)
        assertThat(jobs.countByOwnerIdAndCreatedAtGreaterThanEqual(owner.id, LocalDateTime.now().minusDays(1))).isEqualTo(1)
        verify(client, times(2)).createJob(argThat { this.jobId == jobId })
    }

    @Test
    fun 성공한_결과는_재분석하지_않고_조회한다() {
        val created = service.create(owner.id, request())
        val jobId = requireNotNull(created.jobId)
        whenever(client.getJob(jobId)).thenReturn(
            AiJobStatus(jobId, AiJobRequest.FeatureType.EQUIPMENT_DRAFT_V1, AiJobStatus.Status.SUCCEEDED,
                mapOf("name" to "카메라"), null, "fake", "fake-v1", null, null, null,
                LocalDateTime.now(), LocalDateTime.now())
        )
        assertThat(service.retry(owner.id, jobId).draft).containsEntry("name", "카메라")
        verify(client, times(1)).createJob(any())
    }

    @Test
    fun 동시_요청도_회원별_일일_한도를_넘지_않는다() {
        service.create(owner.id, request())
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val work = Callable { start.await(); tryCreate() }
            val first = executor.submit(work); val second = executor.submit(work)
            start.countDown()
            assertThat(listOf(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("PENDING", "AI_DAILY_LIMIT")
        }
        verify(client, times(2)).createJob(any())
    }

    private fun tryCreate(): String = try { requireNotNull(service.create(owner.id, request()).status) }
    catch (exception: CustomException) { exception.errorCode.name }

    private fun assertError(code: ErrorCode, block: () -> Unit) {
        assertThatThrownBy(block).isInstanceOfSatisfying(CustomException::class.java) { assertThat(it.errorCode).isEqualTo(code) }
    }

    private fun user(status: UserStatus) = users.saveAndFlush(
        User.builder().email("${UUID.randomUUID()}@example.test").name("테스트").password("test-only").status(status).build()
    )
    private fun request() = EquipmentDraftRequest(listOf(upload.objectKey), null, null)
    private fun body(key: String) = """{"imageKeys":["$key"]}"""
    private fun bearer(user: User) = "Bearer ${tokens.generateAccessToken(user.toAuthUser())}"

    private companion object { const val URL = "/api/v1/ai/equipment-drafts" }
}
