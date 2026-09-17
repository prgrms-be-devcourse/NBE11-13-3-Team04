package com.example.iter.auth.domain.repository

import com.example.iter.auth.domain.entity.UserAddress
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserAddressRepository : JpaRepository<UserAddress, Long> {

    fun findByUserIdAndDefaultAddressTrue(userId: Long): Optional<UserAddress>

    fun countByUserIdAndDefaultAddressTrue(userId: Long): Long
}
