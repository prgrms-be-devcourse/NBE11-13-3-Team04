package iter.dispute.domain.repository

import iter.dispute.domain.entity.DisputeResponse
import org.springframework.data.jpa.repository.JpaRepository

interface DisputeResponseRepository : JpaRepository<DisputeResponse, Long> {

    fun findByDisputeIdOrderByCreatedAtAsc(disputeId: Long): List<DisputeResponse>
}
