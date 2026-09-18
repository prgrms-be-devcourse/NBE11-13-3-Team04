package iter.auth.service

import iter.auth.domain.entity.UserAddress
import iter.auth.domain.repository.UserAddressRepository
import iter.auth.domain.repository.UserRepository
import iter.auth.dto.request.AddressUpdateRequest
import iter.auth.dto.response.AddressResponse
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = LoggerFactory.getLogger(UserAddressService::class.java)

@Service
class UserAddressService(
    private val userAddressRepository: UserAddressRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getDefaultAddress(userId: Long): AddressResponse = AddressResponse.from(findDefaultAddress(userId))

    @Transactional
    fun updateDefaultAddress(userId: Long, request: AddressUpdateRequest): AddressResponse {
        // 같은 사용자의 기본 배송지 최초 생성 요청을 직렬화하여
        // is_default=true인 배송지가 중복 생성되는 것을 방지한다.
        userRepository.findWithLockById(userId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        val address = userAddressRepository.findByUserIdAndDefaultAddressTrue(userId)
            .map { existing ->
                existing.update(
                    request.recipientName,
                    request.recipientPhone,
                    request.zipcode,
                    request.address,
                    request.detailAddress,
                )
                existing
            }
            .orElseGet {
                userAddressRepository.save(
                    UserAddress.builder()
                        .userId(userId)
                        .recipientName(request.recipientName)
                        .recipientPhone(request.recipientPhone)
                        .zipcode(request.zipcode)
                        .address(request.address)
                        .detailAddress(request.detailAddress)
                        .defaultAddress(true)
                        .build(),
                )
            }

        log.info("기본 배송지 변경 처리: userId={}, addressId={}", userId, address.id)

        return AddressResponse.from(address)
    }

    private fun findDefaultAddress(userId: Long): UserAddress =
        userAddressRepository.findByUserIdAndDefaultAddressTrue(userId)
            .orElseThrow { CustomException(ErrorCode.ADDRESS_NOT_FOUND) }
}
