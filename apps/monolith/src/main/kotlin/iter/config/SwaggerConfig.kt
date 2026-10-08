package iter.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// (기획서 DoD "Swagger를 통한 API 문서화 및 최신화" 대응)
// 실행 후 확인: http://localhost:8080/swagger-ui.html
@Configuration
class SwaggerConfig {

    @Bean
    fun userApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("user")
        .displayName("User API")
        .pathsToExclude(ADMIN_PATH_PATTERN)
        .build()

    @Bean
    fun adminApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("admin")
        .displayName("Admin API")
        .pathsToMatch(ADMIN_PATH_PATTERN)
        .build()

    @Bean
    fun openAPI(): OpenAPI {
        val bearerScheme = SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .`in`(SecurityScheme.In.HEADER)
            .name("Authorization")

        return OpenAPI()
            .info(
                Info()
                    .title("ITer API")
                    .description("고가 IT장비를 빌리고 빌려주는 P2P 대여 플랫폼 API 명세서")
                    .version("v0.0.1"),
            )
            .addSecurityItem(SecurityRequirement().addList(JWT_SCHEME_NAME))
            .components(Components().addSecuritySchemes(JWT_SCHEME_NAME, bearerScheme))
    }

    companion object {
        private const val JWT_SCHEME_NAME = "JWT"
        private const val ADMIN_PATH_PATTERN = "/api/v1/admin/**"
    }
}
