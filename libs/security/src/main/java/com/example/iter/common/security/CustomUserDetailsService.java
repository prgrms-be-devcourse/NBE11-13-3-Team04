package com.example.iter.common.security;

import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인 시 email/password 검증의 진입점.
// AuthenticationManager -> DaoAuthenticationProvider -> 이 클래스 -> 비밀번호 대조 순서로 호출된다.
//
// 조회는 AuthUserLoader 를 통하고, 없을 때 어떤 예외를 던질지는 이 클래스가 정한다.
// 두 메서드가 서로 다른 예외를 쓰기 때문에 예외 선택을 포트로 넘기지 않았다.
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AuthUserLoader authUserLoader;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AuthUser user = authUserLoader.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        return CustomUserDetails.builder()
                .user(user)
                .build();
    }

    // JWT 필터에서 토큰의 클레임(id)만으로 최신 사용자 상태를 다시 조회할 때 사용.
    // 매 요청 재조회는 정지/탈퇴가 액세스 토큰 만료 전에 반영되게 하려는 의도된 설계다.
    @Transactional(readOnly = true)
    public AuthUser loadUserById(Long id) {
        return authUserLoader.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
