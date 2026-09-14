package com.example.iter.auth.api;

import java.util.Objects;

// 다른 도메인이 회원에게 무언가를 보낼 때 필요한 최소 정보.
// 현재 소비자는 notification 하나이며, 이메일 발송과 언어 선택에 쓰는 네 가지만 담는다.
//
// 화면에 이름만 표시하면 되는 소비자는 이것 대신 UserQueryPort.findSummary 의
// 요약 조회를 쓴다. 필요 없는 필드를 실어 나르면 나중에 REST 로 바꿀 때
// 쓰지도 않는 값 때문에 응답이 커진다.
//
// AuthUser 와 같은 이유로 빌더를 두지 않는다 — 부분 생성된 인스턴스가
// null 필드를 들고 조용히 통과하는 것을 막기 위해 정규 생성자만 남긴다.
// record 는 모든 컴포넌트를 요구하므로 그 성질을 공짜로 얻는다.
public record UserProfile(
        Long userId,
        String name,
        String email,
        PreferredLanguage preferredLanguage
) {
    public UserProfile {
        Objects.requireNonNull(userId, "userId");
        // preferredLanguage 가 null 이면 NotificationMessages 가 조용히 한국어로 떨어진다.
        // User.preferredLanguage 는 nullable = false 이므로 여기서도 강제한다.
        Objects.requireNonNull(preferredLanguage, "preferredLanguage");
        // name, email 은 계약상 null 허용 — 호출부가 대체 문구를 쓴다.
    }
}
