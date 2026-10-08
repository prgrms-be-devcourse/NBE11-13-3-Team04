package iter.auth;

import iter.auth.domain.entity.RefreshToken;
import iter.auth.domain.entity.User;
import iter.auth.domain.repository.RefreshTokenRepository;
import iter.auth.domain.repository.UserRepository;
import iter.support.ApiTestClient;
import iter.support.ApiTestClient.ApiResponse;
import iter.support.MonolithIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFlowIntegrationTest extends MonolithIntegrationTest {

    private static final String REFRESH_COOKIE = "iter-integration-refresh";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void 회원가입_로그인_refresh_회전_재사용_차단_logout이_하나의_흐름으로_동작한다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String email = "auth-" + System.nanoTime() + "@integration.test";

        ApiResponse signedUp = api.post(
                "/api/v1/auth/signup",
                Map.of(
                        "email", email,
                        "password", "Password123!",
                        "name", "통합 테스트",
                        "nickname", "integration",
                        "phone", "010-1234-5678"),
                Map.of());
        assertThat(signedUp.statusCode()).isEqualTo(201);

        ApiResponse loggedIn = api.post(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "Password123!"),
                Map.of());
        assertThat(loggedIn.statusCode()).isEqualTo(200);
        String firstAccessToken = loggedIn.json().get("accessToken").textValue();
        String firstRefreshToken = cookieValue(loggedIn, REFRESH_COOKIE);
        assertThat(firstAccessToken).isNotBlank();
        assertThat(firstRefreshToken).isNotBlank();

        CsrfCookie csrf = issueCsrf(api);
        ApiResponse refreshed = api.post(
                "/api/v1/auth/refresh",
                Map.of(),
                csrfHeaders(csrf, firstRefreshToken, null));
        assertThat(refreshed.statusCode()).isEqualTo(200);
        String secondAccessToken = refreshed.json().get("accessToken").textValue();
        String secondRefreshToken = cookieValue(refreshed, REFRESH_COOKIE);
        assertThat(secondAccessToken).isNotEqualTo(firstAccessToken);
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        ApiResponse me = api.get(
                "/api/v1/users/me",
                Map.of("Authorization", "Bearer " + secondAccessToken));
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.json().get("email").textValue()).isEqualTo(email);

        ApiResponse reused = api.post(
                "/api/v1/auth/refresh",
                Map.of(),
                csrfHeaders(csrf, firstRefreshToken, null));
        assertThat(reused.statusCode()).isEqualTo(401);
        assertThat(reused.json().get("code").textValue()).isEqualTo("INVALID_REFRESH_TOKEN");

        ApiResponse loggedOut = api.post(
                "/api/v1/auth/logout",
                Map.of(),
                csrfHeaders(csrf, secondRefreshToken, secondAccessToken));
        assertThat(loggedOut.statusCode()).isEqualTo(204);
        assertThat(setCookie(loggedOut, REFRESH_COOKIE)).contains("Max-Age=0");

        List<RefreshToken> storedTokens = refreshTokenRepository.findAll();
        assertThat(storedTokens).hasSize(2);
        assertThat(storedTokens).allMatch(RefreshToken::isRevoked);
        assertThat(storedTokens).anyMatch(token -> token.getReplacedByTokenId() != null);
    }

    @Test
    void 정지된_회원은_새_로그인과_refresh가_차단되고_기존_access_token은_거래_처리를_위해_유지된다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String email = "suspended-" + System.nanoTime() + "@integration.test";
        signup(api, email);
        ApiResponse loggedIn = login(api, email);
        String accessToken = loggedIn.json().get("accessToken").textValue();
        String refreshToken = cookieValue(loggedIn, REFRESH_COOKIE);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.suspend();
        userRepository.saveAndFlush(user);

        ApiResponse rejectedLogin = login(api, email);
        assertThat(rejectedLogin.statusCode()).isEqualTo(403);
        assertThat(rejectedLogin.json().get("code").textValue()).isEqualTo("USER_SUSPENDED");

        CsrfCookie csrf = issueCsrf(api);
        ApiResponse rejectedRefresh = api.post(
                "/api/v1/auth/refresh",
                Map.of(),
                csrfHeaders(csrf, refreshToken, null));
        assertThat(rejectedRefresh.statusCode()).isEqualTo(403);
        assertThat(rejectedRefresh.json().get("code").textValue()).isEqualTo("USER_SUSPENDED");

        ApiResponse existingAccess = api.get(
                "/api/v1/users/me",
                Map.of("Authorization", "Bearer " + accessToken));
        assertThat(existingAccess.statusCode()).isEqualTo(200);
        assertThat(existingAccess.json().get("email").textValue()).isEqualTo(email);
    }

    @Test
    void 탈퇴된_회원은_기존_access_token과_로그인과_refresh가_모두_차단된다() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String email = "deleted-" + System.nanoTime() + "@integration.test";
        signup(api, email);
        ApiResponse loggedIn = login(api, email);
        String accessToken = loggedIn.json().get("accessToken").textValue();
        String refreshToken = cookieValue(loggedIn, REFRESH_COOKIE);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.withdraw();
        userRepository.saveAndFlush(user);

        ApiResponse rejectedAccess = api.get(
                "/api/v1/users/me",
                Map.of("Authorization", "Bearer " + accessToken));
        assertThat(rejectedAccess.statusCode()).isEqualTo(401);

        ApiResponse rejectedLogin = login(api, email);
        assertThat(rejectedLogin.statusCode()).isEqualTo(403);
        assertThat(rejectedLogin.json().get("code").textValue()).isEqualTo("USER_DELETED");

        CsrfCookie csrf = issueCsrf(api);
        ApiResponse rejectedRefresh = api.post(
                "/api/v1/auth/refresh",
                Map.of(),
                csrfHeaders(csrf, refreshToken, null));
        assertThat(rejectedRefresh.statusCode()).isEqualTo(403);
        assertThat(rejectedRefresh.json().get("code").textValue()).isEqualTo("USER_DELETED");
    }

    private void signup(ApiTestClient api, String email) {
        ApiResponse response = api.post(
                "/api/v1/auth/signup",
                Map.of(
                        "email", email,
                        "password", "Password123!",
                        "name", "통합 테스트",
                        "nickname", "integration",
                        "phone", "010-1234-5678"),
                Map.of());
        assertThat(response.statusCode()).isEqualTo(201);
    }

    private ApiResponse login(ApiTestClient api, String email) {
        return api.post(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "Password123!"),
                Map.of());
    }

    private CsrfCookie issueCsrf(ApiTestClient api) {
        ApiResponse response = api.get("/api/v1/auth/csrf", Map.of());
        assertThat(response.statusCode()).isEqualTo(204);
        return new CsrfCookie(cookieValue(response, CSRF_COOKIE));
    }

    private Map<String, String> csrfHeaders(
            CsrfCookie csrf,
            String refreshToken,
            String accessToken
    ) {
        String cookies = CSRF_COOKIE + "=" + csrf.value()
                + "; " + REFRESH_COOKIE + "=" + refreshToken;
        if (accessToken == null) {
            return Map.of(
                    "Cookie", cookies,
                    "X-XSRF-TOKEN", csrf.value());
        }
        return Map.of(
                "Cookie", cookies,
                "X-XSRF-TOKEN", csrf.value(),
                "Authorization", "Bearer " + accessToken);
    }

    private String cookieValue(ApiResponse response, String name) {
        String setCookie = setCookie(response, name);
        int valueStart = name.length() + 1;
        int valueEnd = setCookie.indexOf(';', valueStart);
        return setCookie.substring(valueStart, valueEnd < 0 ? setCookie.length() : valueEnd);
    }

    private String setCookie(ApiResponse response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿠키가 응답에 없습니다."));
    }

    private record CsrfCookie(String value) {
    }
}
