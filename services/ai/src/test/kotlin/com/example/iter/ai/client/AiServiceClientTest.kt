package com.example.iter.ai.client

import com.example.iter.ai.dto.AiJobRequest
import com.example.iter.ai.dto.AiJobStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException
import java.util.UUID

class AiServiceClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: AiServiceClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://ai.test")
        server = MockRestServiceServer.bindTo(builder).build()
        client = AiServiceClient(builder.build())
    }

    @Test
    fun 작업을_JSON으로_접수한다() {
        val request = AiJobRequest.create(
            AiJobRequest.FeatureType.REPORT_TRIAGE_V1,
            "report-1",
            mapOf("reason" to "OTHER", "description" to "신고 내용", "targetContext" to emptyMap<String, Any>())
        )
        server.expect(requestTo("http://ai.test/internal/v1/jobs"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.jobId").value(request.jobId.toString()))
            .andExpect(jsonPath("$.schemaVersion").value("1.0"))
            .andExpect(jsonPath("$.source.type").value("REPORT"))
            .andExpect(jsonPath("$.payload.description").value("신고 내용"))
            .andRespond(
                withStatus(HttpStatusCode.valueOf(202))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"jobId":"${request.jobId}","status":"PENDING","duplicate":false}""")
            )

        val accepted = client.createJob(request)

        assertThat(accepted.jobId).isEqualTo(request.jobId)
        assertThat(accepted.status).isEqualTo(AiJobStatus.Status.PENDING)
        assertThat(accepted.duplicate).isFalse()
        server.verify()
    }

    @ParameterizedTest
    @ValueSource(strings = ["PENDING", "PROCESSING", "SUCCEEDED", "FAILED"])
    fun 상태와_결과를_조회한다(status: String) {
        val jobId = UUID.randomUUID()
        server.expect(requestTo("http://ai.test/internal/v1/jobs/$jobId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    {"jobId":"$jobId","featureType":"REPORT_TRIAGE_V1","status":"$status",
                     "result":null,"errorMessage":null,"provider":"fake","model":null,
                     "inputTokens":null,"outputTokens":null,"estimatedCostMicros":null,
                     "createdAt":"2026-09-04T12:00:00","completedAt":null}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val result = client.getJob(jobId)

        assertThat(result.status?.name).isEqualTo(status)
        assertThat(result.isFinished()).isEqualTo(status == "SUCCEEDED" || status == "FAILED")
        assertThat(result.createdAt).hasYear(2026)
        assertThat(result.inputTokens).isNull()
        server.verify()
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 404, 409, 422, 503])
    fun 오류_상태만_전달하고_원문은_노출하지_않는다(status: Int) {
        val jobId = UUID.randomUUID()
        server.expect(requestTo("http://ai.test/internal/v1/jobs/$jobId"))
            .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("private-report-content"))

        assertThatThrownBy { client.getJob(jobId) }
            .isInstanceOfSatisfying(AiServiceException::class.java) { error ->
                assertThat(error.statusCode).isEqualTo(status)
                assertThat(error.message).doesNotContain("private-report-content")
                assertThat(error.cause).isNull()
            }
        server.verify()
    }

    @Test
    fun 연결_실패는_별도_오류로_전달하고_자동_재시도하지_않는다() {
        val jobId = UUID.randomUUID()
        server.expect(requestTo("http://ai.test/internal/v1/jobs/$jobId"))
            .andRespond(withException(IOException("connection failure")))

        assertThatThrownBy { client.getJob(jobId) }
            .isInstanceOfSatisfying(AiServiceException::class.java) { error ->
                assertThat(error.statusCode).isZero()
            }
        server.verify()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "not-json", "{\"status\":\"SUCCEEDED\"}"])
    fun 비어있거나_잘못된_응답을_거절한다(body: String) {
        val jobId = UUID.randomUUID()
        server.expect(requestTo("http://ai.test/internal/v1/jobs/$jobId"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        assertThatThrownBy { client.getJob(jobId) }.isInstanceOf(AiServiceException::class.java)
        server.verify()
    }
}
