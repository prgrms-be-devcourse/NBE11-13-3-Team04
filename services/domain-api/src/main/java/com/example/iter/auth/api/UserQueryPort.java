package com.example.iter.auth.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

// auth 가 다른 도메인에게 공개하는 회원 조회 창구.
//
// 다른 도메인은 UserRepository 나 User 엔티티를 직접 참조하는 대신 이 포트를 쓴다.
// 구현은 auth/support/JpaUserQueryAdapter 이며, 모놀리스에서는 같은 프로세스 안의
// 직접 호출이지만 나중에 서비스가 분리되면 어댑터만 REST 구현으로 교체한다.
// 호출부 코드는 그대로 둔다.
//
// !! 엔티티를 반환하지 않는다 !!
// User 를 넘기면 JPA 영속성 컨텍스트가 모듈 경계를 넘고, REST 로 바꿀 때 호출부를 전부 고쳐야 한다.
//
// !! 메서드는 "무엇을 조회하는가"가 아니라 "무엇을 묻는가"로 짓는다 !!
// 예: countByStatusIn(Collection<UserStatus>) 대신 의도를 담은 이름.
// 상태 집합을 인자로 받으면 열거형이 그대로 다른 도메인에 새어나가고,
// 판단 기준이 바뀔 때마다 호출부도 같이 고쳐야 한다.
public interface UserQueryPort {

    // 전체 회원 수. 탈퇴 회원을 제외하지 않는다 —
    // 필터를 넣으려면 별도 메서드를 만들 것. 기존 UserRepository.count() 와 동작이 같아야 한다.
    long count();

    // 회원에게 무언가를 보내기 위한 정보. 탈퇴·정지 회원도 그대로 돌려준다.
    //
    // 예외를 던지지 않고 Optional 을 돌려주는 이유는 AuthUserLoader 주석과 같다 —
    // 회원을 못 찾았을 때의 처리가 호출 경로마다 다르다. 알림 리스너는 조용히 건너뛰고,
    // 다른 호출부는 USER_NOT_FOUND 를 던진다. 포트가 그걸 정하면 한쪽이 바뀐다.
    Optional<UserProfile> findProfile(Long userId);

    // 화면 표시용 회원 요약. 탈퇴·정지 회원도 그대로 돌려준다.
    Optional<UserSummary> findSummary(Long userId);

    // 여러 명을 한 번에. 못 찾은 ID 는 결과 Map 에서 빠진다 (예외를 던지지 않는다).
    //
    // List 가 아니라 Map 을 돌려주는 이유: 호출부 6곳이 전부
    // findAllById(...).stream().collect(toMap(User::getId, identity())) 를 복붙하고 있었다.
    // 그 조립을 포트 안으로 넣으면 호출부에서 그 블록이 사라진다.
    //
    // !! 한 건씩 루프로 호출하지 말 것 !! N+1 이 된다. 목킹한 테스트는 그대로 통과한다.
    Map<Long, UserSummary> findSummaries(Collection<Long> userIds);

    // 이 회원을 신고 대상으로 삼을 수 있는가. 없는 회원과 탈퇴 회원은 둘 다 false.
    //
    // 호출부에 UserStatus 를 노출하지 않으려고 상태를 돌려주지 않는다.
    // 탈퇴 회원을 "없는 회원"과 같게 볼지는 auth 의 판단이다.
    boolean isReportable(Long userId);
}
