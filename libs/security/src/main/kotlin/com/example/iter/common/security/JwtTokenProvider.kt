package com.example.iter.common.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

private val log = LoggerFactory.getLogger(JwtTokenProvider::class.java)

private const val CLAIM_ROLE = "role"
private const val CLAIM_TOKEN_TYPE = "tokenType"
private const val ACCESS_TOKEN_TYPE = "ACCESS"
private const val REFRESH_TOKEN_TYPE = "REFRESH"

// 토큰 생성/검증/해석을 전담하는 컴포넌트 (token 프로젝트의 TokenProvider와 동일한 설계를 따름)
//
// token 프로젝트와의 차이점 한 가지:
// - token 프로젝트는 토큰 클레임만으로 User를 복원해서 인증 정보를 만들었다.
// - 이 프로젝트는 클레임에서 사용자 id만 꺼내고, 실제 User는 DB에서 다시 조회한다(JwtAuthenticationFilter 참고).
//   -> 이렇게 해야 "회원 상태에 따른 로그인 제한(정지 등)"이 액세스 토큰 만료 전에도 즉시 반영된다.
@Service
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {

    private lateinit var secretKey: SecretKey
    private lateinit var jwtParser: JwtParser

    @PostConstruct
    private fun init() {
        secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secretKey!!))
        jwtParser = Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(jwtProperties.issuer!!)
            .requireAudience(jwtProperties.audience!!)
            .build()
    }

    fun generateAccessToken(user: AuthUser): String =
        generateToken(user, jwtProperties.accessTokenValidity!!, ACCESS_TOKEN_TYPE)

    fun generateRefreshToken(user: AuthUser): String =
        generateToken(user, jwtProperties.refreshTokenValidity!!, REFRESH_TOKEN_TYPE)

    private fun generateToken(user: AuthUser, validity: Duration, tokenType: String): String {
        val now = Date()
        val expiry = Date(now.time + validity.toMillis())
        return Jwts.builder()
            .header().type("JWT").and()
            .issuer(jwtProperties.issuer!!)
            .audience().add(jwtProperties.audience!!).and()
            .issuedAt(now)
            .expiration(expiry)
            .id(UUID.randomUUID().toString())
            .subject(user.id.toString())
            .claim(CLAIM_ROLE, user.role)
            .claim(CLAIM_TOKEN_TYPE, tokenType)
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

    fun validateToken(token: String): TokenStatus = validateToken(token, ACCESS_TOKEN_TYPE)

    fun validateRefreshToken(token: String): TokenStatus = validateToken(token, REFRESH_TOKEN_TYPE)

    private fun validateToken(token: String, expectedTokenType: String): TokenStatus {
        return try {
            val claims = jwtParser.parseSignedClaims(token).payload
            if (expectedTokenType != claims.get(CLAIM_TOKEN_TYPE, String::class.java)) {
                return TokenStatus.INVALID
            }
            TokenStatus.VALID
        } catch (e: ExpiredJwtException) {
            log.debug("만료된 토큰")
            TokenStatus.EXPIRED
        } catch (e: Exception) {
            log.debug("유효하지 않은 토큰: {}", e.message)
            TokenStatus.INVALID
        }
    }

    fun getUserId(token: String): Long = getClaims(token).subject.toLong()

    fun getExpiration(token: String): LocalDateTime {
        val expiration = getClaims(token).expiration
        return LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault())
    }

    private fun getClaims(token: String): Claims = jwtParser.parseSignedClaims(token).payload

    // 이미 조회해 둔 CustomUserDetails로 시큐리티 인증 객체를 만든다.
    fun getAuthentication(principal: CustomUserDetails): Authentication =
        UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
}
