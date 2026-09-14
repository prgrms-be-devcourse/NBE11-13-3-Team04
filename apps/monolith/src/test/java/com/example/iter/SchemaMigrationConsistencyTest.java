package com.example.iter;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// 마이그레이션 SQL 과 엔티티가 어긋나지 않는지 검증한다.
//
// 나머지 테스트는 엔티티에서 바로 스키마를 만들기(create-drop) 때문에,
// db/migration 의 SQL 이 틀려도 아무도 모른다. 로컬에서 앱을 띄울 때야 발견된다.
//
// 여기서는 반대로 한다 — Flyway 로 스키마를 만들고 Hibernate 가 엔티티와 맞는지 검증한다(validate).
// 엔티티에 필드를 추가하고 마이그레이션을 안 쓰면 이 테스트가 기동 단계에서 실패한다.
//
// 다른 테스트와 H2 인스턴스를 분리한다(jdbc:h2:mem:schema-check). 같은 DB 를 쓰면
// create-drop 이 Flyway 가 만든 스키마를 지워버려 검증이 무의미해진다.
//
// 참고: 마이그레이션 SQL 은 MySQL 방언으로 쓰여 있고 H2 는 MODE=MySQL 로 그것을 해석한다.
// 둘이 100% 같지는 않으므로, MySQL 에서만 터지는 문법은 여기서 못 잡는다.
// 그건 로컬 기동 때 걸린다.
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:schema-check;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class SchemaMigrationConsistencyTest {

    @Autowired
    private Flyway flyway;

    // 컨텍스트가 뜬 것 자체가 "마이그레이션 스키마 == 엔티티" 라는 증거다.
    // validate 가 틀리면 기동 단계에서 예외가 나 이 테스트 메서드에 도달하지 못한다.
    //
    // 아래 단언은 "Flyway 가 실제로 뭔가 적용했는지"를 확인한다.
    // 마이그레이션이 0건인데 통과하면 이 테스트는 아무것도 검증하지 않는 셈이다.
    @Test
    void 마이그레이션으로_만든_스키마가_엔티티와_일치한다() {
        assertThat(flyway.info().applied())
                .as("적용된 마이그레이션이 없다면 이 테스트는 빈 스키마를 검증한 것이다")
                .isNotEmpty();
    }
}
