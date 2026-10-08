package iter.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Presigned URL 서명만 로컬에서 수행한다. S3 요청은 각 테스트가 별도로 스텁한다. */
@TestConfiguration
public class IntegrationS3Presigner {

    @Bean
    @Primary
    S3Presigner integrationS3Presigner() {
        return S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("integration-test", "integration-test-secret")))
                .build();
    }
}
