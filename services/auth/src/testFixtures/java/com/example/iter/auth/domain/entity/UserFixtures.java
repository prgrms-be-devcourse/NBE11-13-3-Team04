package com.example.iter.auth.domain.entity;

import com.example.iter.common.security.Role;
import com.example.iter.common.security.UserStatus;

// 다른 도메인 모듈의 단위 테스트가 User 엔티티를 직접 만들어야 할 때 쓴다.
// testFixtures(project(':services:auth'))로 가져다 쓴다.
public final class UserFixtures {

    private UserFixtures() {
    }

    public static User user(Long id, String name, String nickname, UserStatus status, Role role) {
        return User.builder()
                .id(id)
                .email(name + "@iter.test")
                .password("encoded-password")
                .name(name)
                .nickname(nickname)
                .role(role)
                .status(status)
                .build();
    }
}
