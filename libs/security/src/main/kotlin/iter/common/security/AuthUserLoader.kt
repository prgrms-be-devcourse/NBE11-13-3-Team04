package iter.common.security

import java.util.Optional

// common/security 가 auth 도메인을 모르게 유지하기 위한 조회 창구.
// 같은 저장소의 SseTicketResolver(구현은 notification), MailService(구현은 JavaMailService)와
// 동일한 패턴이다.
//
// Optional 을 반환하고 예외는 던지지 않는다.
// 현재 두 호출 경로가 서로 다른 예외를 쓰기 때문이다:
//   loadUserByUsername -> UsernameNotFoundException (시큐리티 계약)
//   loadUserById       -> CustomException(USER_NOT_FOUND)
// 이 인터페이스가 예외를 정하면 둘 중 하나가 바뀌어 응답 코드가 달라진다.
//
// !! 구현체는 문자 그대로 id/email 로만 조회해야 한다 !!
// 상태 필터(예: AND status <> DELETED)를 추가하면 JwtAuthenticationFilter 의
// 탈퇴 회원 경로가 "찾은 뒤 경고 로그 + 조용한 미인증(401)"에서
// "못 찾음 -> CustomException 전파"로 바뀐다. 상태 코드도 로그도 달라지는데
// 컴파일도 되고 단위 테스트도 통과하므로 발견하기 어렵다.
interface AuthUserLoader {

    fun findByEmail(email: String): Optional<AuthUser>

    // id 를 nullable Long 으로 선언한다 — 구현체(JpaAuthUserLoader)가 아직 자바라 Long
    // 파라미터가 boxed 참조형이다. 코틀린 non-null Long 은 바이트코드에서 primitive long 으로
    // 컴파일돼 자바 구현체의 override 시그니처가 깨진다.
    fun findById(id: Long?): Optional<AuthUser>
}
