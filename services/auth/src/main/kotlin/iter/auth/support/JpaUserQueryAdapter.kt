package iter.auth.support

import iter.auth.api.UserProfile
import iter.auth.api.UserQueryPort
import iter.auth.api.UserSummary
import iter.auth.domain.entity.User
import iter.auth.domain.repository.UserRepository
import iter.common.security.UserStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

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
class JpaUserQueryAdapter(
    private val userRepository: UserRepository,
) : UserQueryPort {

    @Transactional(readOnly = true)
    override fun count(): Long = userRepository.count()

    @Transactional(readOnly = true)
    override fun findProfile(userId: Long?): Optional<UserProfile> =
        userRepository.findById(userId!!).map(::toProfile)

    @Transactional(readOnly = true)
    override fun findSummary(userId: Long?): Optional<UserSummary> =
        userRepository.findSummaryById(userId!!)

    @Transactional(readOnly = true)
    override fun findSummaries(userIds: Collection<Long>): Map<Long, UserSummary> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }
        return userRepository.findSummariesByIdIn(userIds).associateBy { it.userId }
    }

    @Transactional(readOnly = true)
    override fun isReportable(userId: Long?): Boolean =
        userRepository.findById(userId!!)
            .map { user -> user.status != UserStatus.DELETED }
            .orElse(false)

    // 매핑을 UserProfile 의 static 팩터리가 아니라 어댑터에 두는 이유:
    // UserProfile.from(User) 를 만들면 auth.api 가 auth.domain.entity 를 알게 되고,
    // 3차에서 api 를 별도 아티팩트로 떼어낼 때 엔티티가 딸려 나온다.
    private fun toProfile(user: User): UserProfile =
        UserProfile(user.id!!, user.name, user.email, user.preferredLanguage)
}
