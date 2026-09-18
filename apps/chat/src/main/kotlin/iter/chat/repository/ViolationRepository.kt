package iter.chat.repository

import iter.chat.domain.Violation
import java.time.Instant
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ViolationRepository : CoroutineCrudRepository<Violation, Long> {

    // 경고 3회/5회 같은 누적 제재 판단에 쓴다(idx_user_occurred).
    suspend fun countByUserIdAndOccurredAtAfter(userId: Long, after: Instant): Long
}
