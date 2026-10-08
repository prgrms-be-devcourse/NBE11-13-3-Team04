package iter.auth.api

// auth 가 공개하는 회원 행 잠금 창구.
//
// !! UserQueryPort 와 일부러 타입을 나눴다 !!
// findSummary 와 lockUser 는 반환 타입이 비슷해서 한 인터페이스에 두면
// 호출부에서 바꿔 써도 컴파일이 된다. 타입이 다르면 "락이 필요한 자리"가
// 주입 필드 목록에서 눈에 보인다.
//
// 또한 조회 어댑터는 readOnly = true 지만 이 어댑터는 MANDATORY 다.
// 계약이 다르므로 클래스도 달라야 한다.
//
// !! 서비스가 분리돼도 이 포트만은 REST 로 바꿀 수 없다 !!
// 행 락은 트랜잭션에 묶여 있어 네트워크 너머로 전달되지 않는다.
// 이 포트를 쓰는 곳은 "auth 와 같은 DB 에 있어야 하는 코드"라는 표시다.
interface UserLockPort {

    // 전달된 회원들을 잠그고 잠근 시점의 상태를 돌려준다.
    // 못 찾은 ID 는 결과 Map 에서 빠진다 (예외를 던지지 않는다).
    //
    // !! 잠그는 순서는 이 구현의 계약이며 호출자가 정하지 않는다 !!
    // 두 트랜잭션이 같은 두 회원을 반대 순서로 잠그면 데드락이 난다
    // (A가 B의 장비를, B가 A의 장비를 동시에 빌리는 경우).
    // 정렬을 호출자에 맡기면 두 번째 호출자가 반드시 잊는다 —
    // 컴파일도 단위 테스트도 이를 잡지 못한다.
    // 회귀는 RentalCreationDeadlockTest 가 잡는다.
    //
    // 반대로 "어떤 상태가 차단 사유인가"는 호출자의 정책이다.
    // 대여 생성은 USER_SUSPENDED/USER_DELETED/EQUIPMENT_NOT_AVAILABLE 셋을 쓰고
    // 신고 접수는 앞의 둘만 쓴다. 포트가 예외를 정하면 한쪽이 바뀐다.
    fun lockAll(userIds: Collection<Long>): Map<Long, UserLockView>
}
