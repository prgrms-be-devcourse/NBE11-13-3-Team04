package com.example.iter.auth.api;

import java.util.Objects;

// 다른 도메인이 화면에 회원을 표시할 때 쓰는 최소 정보.
// 대여 목록의 상대방, 신고 목록의 신고자, 장비 목록의 등록자가 전부 이것 하나로 수렴한다.
//
// !! 컴포넌트 이름을 바꾸지 말 것 !!
// 이 record 는 HTTP 응답 본문에 그대로 직렬화된다. nickName 의 대문자 N 을 nickname 으로
// "정리"하면 6개 엔드포인트의 응답 필드명이 조용히 바뀐다.
//
// from(User) 같은 매핑 팩터리를 두지 않는다. auth.api 가 auth.domain.entity 를 알게 되면
// 3차에서 api 를 별도 아티팩트로 떼어낼 때 엔티티가 딸려 나온다.
// 매핑은 auth/support/JpaUserQueryAdapter 안에 있다.
public record UserSummary(
        Long userId,
        String nickName
) {
    public UserSummary {
        Objects.requireNonNull(userId, "userId");
        // nickName 은 계약상 null 허용 — User.nickname 이 nullable 이다.
    }
}
