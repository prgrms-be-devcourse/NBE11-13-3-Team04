package com.example.iter.dispute.domain.repository

import com.example.iter.dispute.domain.entity.Dispute
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface DisputeRepository : JpaRepository<Dispute, Long> {

    fun findFirstByRentalIdOrderByCreatedAtDesc(rentalId: Long): Optional<Dispute>
}
