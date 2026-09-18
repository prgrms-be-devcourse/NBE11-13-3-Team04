package iter.dispute.domain.repository

import iter.dispute.domain.entity.DisputeImage
import org.springframework.data.jpa.repository.JpaRepository

interface DisputeImageRepository : JpaRepository<DisputeImage, Long>
