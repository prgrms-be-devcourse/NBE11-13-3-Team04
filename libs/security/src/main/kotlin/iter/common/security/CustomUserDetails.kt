package iter.common.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

// Spring Security와 인증 사용자 사이의 어댑터.
// 도메인 엔티티가 UserDetails를 직접 구현하지 않고 감싸는 방식을 사용한다.
// @AuthenticationPrincipal CustomUserDetails 로 컨트롤러/서비스에서 바로 꺼내 쓸 수 있다.
//
// 필드 이름 'user'는 바꾸지 않는다. 코드베이스 51곳이 principal.getUser().getId() /
// .getRole() 형태로 호출하고 있다. AuthUser가 같은 이름의 빈 스타일 게터를 제공하므로
// 호출부는 그대로 컴파일된다.
//
// builder()/user()/build() 체이닝은 Lombok @Builder가 만들어주던 것과 동일한 API를
// 그대로 유지하려고 직접 둔다 — 자바 쪽 호출부(테스트 다수 포함)가 이 형태를 그대로 쓴다.
class CustomUserDetails private constructor(
    val user: AuthUser,
) : UserDetails {

    // ROLE_ 접두사는 Spring Security 표준 규칙 — hasRole("ADMIN")은 내부적으로 "ROLE_ADMIN" 권한을 찾는다.
    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))

    override fun getPassword(): String? = user.password

    // 시큐리티의 username = 로그인 식별자. 이 프로젝트는 email로 로그인하므로 email을 반환한다.
    // UserDetails.getUsername()은 non-null 계약이다 — email은(password와 달리) 항상 있다.
    override fun getUsername(): String = user.email!!

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean =
        // 정지 회원도 기존 거래 처리를 위해 인증은 유지하고 신규 거래를 서비스 인가에서 차단한다.
        true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean =
        // 탈퇴(DELETED)한 계정은 비활성 처리
        user.status != UserStatus.DELETED

    class Builder {
        private lateinit var user: AuthUser

        fun user(user: AuthUser): Builder = apply { this.user = user }

        fun build(): CustomUserDetails = CustomUserDetails(user)
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
