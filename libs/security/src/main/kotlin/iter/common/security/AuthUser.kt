package iter.common.security

// 시큐리티 계층이 auth 도메인의 User 엔티티 대신 들고 다니는 최소 스냅샷.
// 코드베이스 전체를 조사한 결과 시큐리티가 실제로 필요로 하는 멤버는 아래 5개뿐이다.
//
// 의도적으로 빌더를 두지 않는다:
// User.builder()는 @Builder.Default로 role=USER, status=ACTIVE를 채워주지만
// 여기에 빌더를 열어두면 AuthUser.builder().id(1L).build() 같은 호출이 가능해진다.
// 그러면 status가 null이 되어 isEnabled()는 true를 반환하고
// status == UserStatus.DELETED 비교는 false가 되므로,
// 상태를 알 수 없는 principal이 조용히 인증을 통과한다. 컴파일도 테스트도 이를 잡지 못한다.
// 생성 경로를 User#toAuthUser() 하나로 묶고 필수 필드를 생성자에서 강제하는 이유다.
class AuthUser private constructor(
    val id: Long,
    val email: String?,
    // UserDetails.getPassword() 계약용. OAuth로 가입한 회원은 비밀번호가 없어 null일 수 있다.
    val password: String?,
    val role: Role,
    val status: UserStatus,
) {

    // 메서드 보안 SpEL이 도메인 User 엔티티 없이도 현재 계정 활성 상태를 판별한다.
    fun isActive(): Boolean = status == UserStatus.ACTIVE

    companion object {
        @JvmStatic
        fun of(id: Long, email: String?, password: String?, role: Role, status: UserStatus): AuthUser =
            AuthUser(id, email, password, role, status)
    }
}
