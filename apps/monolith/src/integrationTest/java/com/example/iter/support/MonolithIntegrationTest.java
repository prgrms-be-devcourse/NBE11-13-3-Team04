package com.example.iter.support;

import com.example.iter.common.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("integration-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({IntegrationTestContainers.class, IntegrationS3Presigner.class})
@ResourceLock("monolith-integration-infrastructure")
public abstract class MonolithIntegrationTest {

    protected static final TossStub TOSS = TossStub.start();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private MailService mailService;

    @DynamicPropertySource
    static void tossProperties(DynamicPropertyRegistry registry) {
        registry.add("toss.base-url", TOSS::baseUrl);
    }

    @BeforeEach
    final void cleanIntegrationState() {
        new DatabaseCleaner(jdbcTemplate, redisTemplate).clean();
        TOSS.reset();
    }
}
