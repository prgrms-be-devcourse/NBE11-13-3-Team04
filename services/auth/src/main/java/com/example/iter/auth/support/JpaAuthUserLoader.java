package com.example.iter.auth.support;

import com.example.iter.auth.domain.entity.User;
import com.example.iter.auth.domain.repository.UserRepository;
import com.example.iter.common.security.AuthUser;
import com.example.iter.common.security.AuthUserLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// common/security 의 AuthUserLoader 를 auth 도메인이 구현한다.
// 이 방향(도메인 -> 공용)이 정상이며, 덕분에 common/security 는 auth 를 모른다.
//
// 조회 조건을 추가하지 말 것. AuthUserLoader 의 주석 참고 -
// 상태 필터를 넣으면 탈퇴 회원의 응답 코드와 로그가 조용히 달라진다.
// 탈퇴 여부 판단은 호출자(JwtAuthenticationFilter, CustomUserDetails.isEnabled)의 책임이다.
@Component
@RequiredArgsConstructor
public class JpaAuthUserLoader implements AuthUserLoader {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthUser> findByEmail(String email) {
        return userRepository.findByEmail(email).map(User::toAuthUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthUser> findById(Long id) {
        return userRepository.findById(id).map(User::toAuthUser);
    }
}
