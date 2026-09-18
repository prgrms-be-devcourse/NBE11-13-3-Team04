package iter.auth.dto.response

import iter.auth.domain.entity.UserAddress

@JvmRecord
data class AddressResponse(
    val id: Long?,
    val recipientName: String,
    val recipientPhone: String,
    val zipcode: String,
    val address: String,
    val detailAddress: String,
) {
    companion object {
        @JvmStatic
        fun from(address: UserAddress): AddressResponse =
            AddressResponse(
                address.id,
                address.recipientName,
                address.recipientPhone,
                address.zipcode,
                address.address,
                address.detailAddress,
            )
    }
}
