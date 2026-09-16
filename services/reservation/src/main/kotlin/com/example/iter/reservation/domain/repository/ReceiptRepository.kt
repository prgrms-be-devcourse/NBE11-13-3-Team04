package com.example.iter.reservation.domain.repository

import com.example.iter.reservation.domain.entity.Receipt
import org.springframework.data.jpa.repository.JpaRepository

import java.util.Optional

interface ReceiptRepository : JpaRepository<Receipt, Long> {
    fun findByRentalId(rentalId: Long): Optional<Receipt>
}
