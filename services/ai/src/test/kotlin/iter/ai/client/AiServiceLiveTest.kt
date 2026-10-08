package iter.ai.client

import iter.ai.config.AiServiceProperties
import iter.ai.dto.AiJobRequest
import iter.ai.dto.AiJobStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

// 별도로 실행한 로컬 Fake 서버만 대상으로 합니다. 일반 test 실행 시에는 건너뜁니다.
@EnabledIfEnvironmentVariable(named = "AI_LIVE_TEST", matches = "true")
@Tag("system")
class AiServiceLiveTest {
    @Test
    fun 실제_Python_Fake_서버에_세_기능을_접수하고_조회한다() {
        val properties = AiServiceProperties().apply {
            baseUrl = System.getenv().getOrDefault("AI_SERVICE_BASE_URL", "http://localhost:8000")
            internalApiKey = System.getenv().getOrDefault("AI_INTERNAL_API_KEY", "")
        }
        val client = AiServiceClient(properties)
        val mapper = JsonMapper.builder().build()

        if (properties.internalApiKey.isNotBlank()) {
            val wrongKeyProperties = AiServiceProperties().apply {
                baseUrl = properties.baseUrl
                internalApiKey = "intentionally-wrong-test-key"
            }
            val unauthorizedClient = AiServiceClient(wrongKeyProperties)
            assertThatThrownBy { unauthorizedClient.getJob(UUID.randomUUID()) }
                .isInstanceOfSatisfying(AiServiceException::class.java) { error ->
                    assertThat(error.statusCode).isEqualTo(401)
                }
        }

        listOf("equipment-draft-job", "report-triage-job", "return-condition-job").forEach { fixture ->
            val path = Path.of(System.getProperty("integration.contract.dir"))
                .resolve("ai/jobs/$fixture.json")
            val template = mapper.readValue(Files.readString(path), AiJobRequest::class.java)
            val request = AiJobRequest.create(
                requireNotNull(template.featureType),
                "spring-smoke-${UUID.randomUUID()}",
                template.payload
            )
            val accepted = client.createJob(request)
            assertThat(accepted.jobId).isEqualTo(request.jobId)
            assertThat(accepted.duplicate).isFalse()

            var result = client.getJob(requireNotNull(request.jobId))
            repeat(20) {
                if (result.isFinished()) return@repeat
                Thread.sleep(250)
                result = client.getJob(requireNotNull(request.jobId))
            }
            assertThat(result.status).isEqualTo(AiJobStatus.Status.SUCCEEDED)
            assertThat(result.featureType).isEqualTo(request.featureType)
            assertThat(result.provider).isEqualTo("fake")
            assertThat(result.result).isNotEmpty()
            assertThat(client.createJob(request).duplicate).isTrue()

            val conflicting = AiJobRequest(
                request.schemaVersion,
                request.jobId,
                request.featureType,
                AiJobRequest.Source(request.source?.type, "different-source"),
                request.inputHash,
                request.payload
            )
            assertThatThrownBy { client.createJob(conflicting) }
                .isInstanceOfSatisfying(AiServiceException::class.java) { error ->
                    assertThat(error.statusCode).isEqualTo(409)
                }
            println("AI live test jobId=${request.jobId}")
        }

        assertThatThrownBy { client.getJob(UUID.randomUUID()) }
            .isInstanceOfSatisfying(AiServiceException::class.java) { error ->
                assertThat(error.statusCode).isEqualTo(404)
            }
    }
}
