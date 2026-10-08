package iter.auth.support

import iter.auth.api.UserLockPort
import iter.auth.api.UserLockView
import iter.auth.domain.entity.User
import iter.auth.domain.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

// auth/api/UserLockPort 의 모놀리스 구현.
//
// !! propagation = MANDATORY 다 !!
// 기본값(REQUIRED)이면 호출자에 트랜잭션이 없을 때 이 메서드가 자기 트랜잭션을 열고
// 리턴하는 순간 락을 놓아버린다. 호출자는 락을 잡았다고 믿고 계속 진행한다.
// 컴파일도 되고 목킹한 단위 테스트도 통과한다. MANDATORY 는 그 상황을 즉시 예외로 만든다.
//
// !! readOnly 를 붙이지 말 것 !!
// 락은 쓰기 트랜잭션에서만 의미가 있다.
@Component
class JpaUserLockAdapter(
    private val userRepository: UserRepository,
) : UserLockPort {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lockAll(userIds: Collection<Long>): Map<Long, UserLockView> {
        // 정렬이 이 클래스의 존재 이유다. 호출 순서를 호출자에게 맡기지 않는다.
        // 중복 ID 는 제거한다 — 같은 행을 두 번 잠글 이유가 없다.
        val orderedIds = userIds.distinct().sorted()

        val locked = LinkedHashMap<Long, UserLockView>()
        for (userId in orderedIds) {
            userRepository.findWithLockById(userId)
                .map(::toView)
                .ifPresent { view -> locked[view.userId] = view }
        }
        return locked
    }

    private fun toView(user: User): UserLockView = UserLockView(user.id!!, user.status)
}
