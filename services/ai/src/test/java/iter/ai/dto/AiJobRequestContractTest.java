package iter.ai.dto;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("contract")
class AiJobRequestContractTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @ParameterizedTest
    @MethodSource("fixtures")
    void Python과_공유하는_AI_요청_fixture를_Spring_DTO가_읽고_쓸_수_있다(
            String fileName,
            AiJobRequest.FeatureType featureType,
            String sourceType
    ) throws IOException {
        Path fixture = Path.of(System.getProperty("integration.contract.dir"))
                .resolve("ai/jobs")
                .resolve(fileName);

        AiJobRequest request = jsonMapper.readValue(fixture.toFile(), AiJobRequest.class);
        String serialized = jsonMapper.writeValueAsString(request);

        assertThat(request.schemaVersion()).isEqualTo("1.0");
        assertThat(request.featureType()).isEqualTo(featureType);
        assertThat(request.source().type()).isEqualTo(sourceType);
        assertThat(request.jobId()).isNotNull();
        assertThat(request.inputHash()).matches("sha256:[a-f0-9]{64}");
        assertThat(serialized).contains(
                "\"schemaVersion\"",
                "\"jobId\"",
                "\"featureType\"",
                "\"inputHash\"",
                "\"payload\"");
    }

    private static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of(
                        "return-condition-job.json",
                        AiJobRequest.FeatureType.RETURN_CONDITION_V1,
                        "RENTAL"),
                Arguments.of(
                        "report-triage-job.json",
                        AiJobRequest.FeatureType.REPORT_TRIAGE_V1,
                        "REPORT"),
                Arguments.of(
                        "equipment-draft-job.json",
                        AiJobRequest.FeatureType.EQUIPMENT_DRAFT_V1,
                        "UPLOAD_BUNDLE")
        );
    }
}
