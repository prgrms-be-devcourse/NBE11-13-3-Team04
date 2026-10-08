package iter.auth.support;

import iter.auth.api.UserLockPort;
import iter.auth.api.UserLockView;
import iter.auth.domain.entity.User;
import iter.auth.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 커맨드·락 어댑터의 @Transactional(propagation = MANDATORY) 규약을 한 번 기계적으로 증명한다.
// 이후 PR 에서 만드는 커맨드 포트들도 같은 규약을 따르지만, 규약 자체가 동작한다는 증거는 여기 하나면 된다.
//
// 기본값(REQUIRED)이었다면 트랜잭션 없이 호출해도 어댑터가 자기 트랜잭션을 열어 조용히 성공하고,
// 리턴하는 순간 락이 풀린다. 호출자는 락을 잡았다고 믿고 계속 진행한다.
@ActiveProfiles("test")
@SpringBootTest
class JpaUserLockAdapterTest {

    @Autowired
    private UserLockPort userLockPort;
    @Autowired
    private UserRepository userRepository;

    @Test
    void 트랜잭션_없이_호출하면_조용히_성공하지_않고_실패한다() {
        assertThatThrownBy(() -> userLockPort.lockAll(List.of(1L)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @Transactional
    void 호출자_트랜잭션_안에서는_잠근_시점의_상태를_돌려준다() {
        User user = userRepository.save(user("lock-" + System.nanoTime()));

        Map<Long, UserLockView> locked = userLockPort.lockAll(List.of(user.getId()));

        assertThat(locked).containsOnlyKeys(user.getId());
        assertThat(locked.get(user.getId()).status()).isEqualTo(user.getStatus());
    }

    @Test
    @Transactional
    void 없는_회원은_결과에서_빠질_뿐_예외를_던지지_않는다() {
        User user = userRepository.save(user("lock-partial-" + System.nanoTime()));

        Map<Long, UserLockView> locked = userLockPort.lockAll(List.of(user.getId(), -1L));

        assertThat(locked).containsOnlyKeys(user.getId());
    }

    private User user(String tag) {
        return User.builder()
                .email(tag + "@test.com")
                .password("test-password")
                .name("락테스트")
                .pointBalance(BigDecimal.ZERO)
                .build();
    }
}
