package iter.delivery.domain.repository

import iter.delivery.domain.entity.Shipping
import iter.delivery.domain.entity.ShippingType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ShippingRepository : JpaRepository<Shipping, Long> {
    fun findByRentalId(rentalId: Long): List<Shipping>

    fun findByRentalIdAndType(rentalId: Long, type: ShippingType): Optional<Shipping>
}
