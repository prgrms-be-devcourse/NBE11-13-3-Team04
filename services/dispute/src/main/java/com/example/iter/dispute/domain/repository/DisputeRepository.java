package com.example.iter.dispute.domain.repository;

import com.example.iter.dispute.domain.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    Optional<Dispute> findFirstByRentalIdOrderByCreatedAtDesc(Long rentalId);
}
