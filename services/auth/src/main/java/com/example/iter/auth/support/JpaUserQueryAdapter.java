package com.example.iter.auth.support;

import com.example.iter.auth.api.UserProfile;
import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.auth.api.UserSummary;
import com.example.iter.auth.domain.entity.User;
import com.example.iter.auth.domain.repository.UserRepository;
import com.example.iter.common.security.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// auth/api/UserQueryPort 의 모놀리스 구현. 같은 프로세스이므로 리포지토리를 직접 호출한다.
// 서비스가 분리되면 이 클래스만 RestUserQueryAdapter 로 교체한다.
//
// readOnly = true 는 JpaAuthUserLoader 와 같은 이유다 — 호출자 트랜잭션이 있으면 참여하고,
// 없으면 자기 읽기 트랜잭션을 연다. 조회 어댑터는 어떤 엔티티도 변경하지 않는다.
//
// !! 조회 조건을 추가하지 말 것 !!
// 포트 주석에 적힌 대로 동작해야 한다. 여기서 조용히 필터를 넣으면
// 호출부는 숫자가 왜 달라졌는지 알 방법이 없다.
@Component
@RequiredArgsConstructor
public class JpaUserQueryAdapter implements UserQueryPort {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return userRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> findProfile(Long userId) {
        return userRepository.findById(userId).map(JpaUserQueryAdapter::toProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findSummary(Long userId) {
        return userRepository.findSummaryById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, UserSummary> findSummaries(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findSummariesByIdIn(userIds).stream()
                .collect(Collectors.toMap(UserSummary::userId, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReportable(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus() != UserStatus.DELETED)
                .orElse(false);
    }

    // 매핑을 UserProfile 의 static 팩터리가 아니라 어댑터에 두는 이유:
    // UserProfile.from(User) 를 만들면 auth.api 가 auth.domain.entity 를 알게 되고,
    // 3차에서 api 를 별도 아티팩트로 떼어낼 때 엔티티가 딸려 나온다.
    private static UserProfile toProfile(User user) {
        return new UserProfile(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPreferredLanguage()
        );
    }
}
