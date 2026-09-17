package com.example.iter.schema;

import com.example.iter.support.MonolithIntegrationTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** 빈 MySQL에 Flyway를 적용한 스키마와 JPA 엔티티가 함께 기동되는지 검증한다. */
class MySqlSchemaMigrationIntegrationTest extends MonolithIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void 빈_MySQL에_전체_마이그레이션을_적용하고_엔티티를_검증한다() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }

        assertThat(flyway.info().applied())
                .as("빈 MySQL에 실제 적용된 Flyway 마이그레이션이 있어야 한다")
                .isNotEmpty();

        // spring.jpa.hibernate.ddl-auto=validate이므로 컨텍스트가 기동된 사실이
        // Flyway 스키마와 엔티티가 일치한다는 검증이다.
        assertThat(flyway.info().current()).isNotNull();
    }
}
