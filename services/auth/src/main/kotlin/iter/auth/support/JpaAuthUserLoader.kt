package iter.auth.support

import iter.auth.domain.entity.User
import iter.auth.domain.repository.UserRepository
import iter.common.security.AuthUser
import iter.common.security.AuthUserLoader
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

// common/security 의 AuthUserLoader 를 auth 도메인이 구현한다.
// 이 방향(도메인 -> 공용)이 정상이며, 덕분에 common/security 는 auth 를 모른다.
//
// 조회 조건을 추가하지 말 것. AuthUserLoader 의 주석 참고 -
// 상태 필터를 넣으면 탈퇴 회원의 응답 코드와 로그가 조용히 달라진다.
// 탈퇴 여부 판단은 호출자(JwtAuthenticationFilter, CustomUserDetails.isEnabled)의 책임이다.
@Component
class JpaAuthUserLoader(
    private val userRepository: UserRepository,
) : AuthUserLoader {

    @Transactional(readOnly = true)
    override fun findByEmail(email: String): Optional<AuthUser> =
        userRepository.findByEmail(email).map(User::toAuthUser)

    @Transactional(readOnly = true)
    override fun findById(id: Long?): Optional<AuthUser> =
        userRepository.findById(id!!).map(User::toAuthUser)
}
