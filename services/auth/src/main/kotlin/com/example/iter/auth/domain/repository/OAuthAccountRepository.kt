package com.example.iter.auth.domain.repository

import com.example.iter.auth.domain.entity.OAuthAccount
import com.example.iter.auth.domain.entity.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OAuthAccountRepository : JpaRepository<OAuthAccount, Long> {

    fun findByProviderAndProviderUserId(provider: OAuthProvider, providerUserId: String): Optional<OAuthAccount>

    fun existsByUserIdAndProvider(userId: Long, provider: OAuthProvider): Boolean
}
