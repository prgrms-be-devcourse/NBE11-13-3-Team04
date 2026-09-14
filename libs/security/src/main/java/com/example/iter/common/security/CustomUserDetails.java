package com.example.iter.common.security;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Spring Security와 인증 사용자 사이의 어댑터.
// 도메인 엔티티가 UserDetails를 직접 구현하지 않고 감싸는 방식을 사용한다.
// @AuthenticationPrincipal CustomUserDetails 로 컨트롤러/서비스에서 바로 꺼내 쓸 수 있다.
//
// 필드 이름 'user'는 바꾸지 않는다. Lombok이 이 이름으로 getUser()를 만들고,
// 코드베이스 51곳이 principal.getUser().getId() / .getRole() 형태로 호출하고 있다.
// AuthUser가 같은 이름의 빈 스타일 게터를 제공하므로 호출부는 그대로 컴파일된다.
@Getter
@Builder
public class CustomUserDetails implements UserDetails {

    private final AuthUser user;

    // ROLE_ 접두사는 Spring Security 표준 규칙 — hasRole("ADMIN")은 내부적으로 "ROLE_ADMIN" 권한을 찾는다.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    // 시큐리티의 username = 로그인 식별자. 이 프로젝트는 email로 로그인하므로 email을 반환한다.
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // 정지 회원도 기존 거래 처리를 위해 인증은 유지하고 신규 거래를 서비스 인가에서 차단한다.
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 탈퇴(DELETED)한 계정은 비활성 처리
        return user.getStatus() != UserStatus.DELETED;
    }
}
