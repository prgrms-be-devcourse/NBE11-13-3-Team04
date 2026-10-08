package iter.auth.support

import iter.auth.service.model.KakaoUserInfo
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Component

@Component
class KakaoOAuth2UserInfoMapper {

    fun map(oAuth2User: OAuth2User): KakaoUserInfo {
        val id = oAuth2User.getAttribute<Any>("id") ?: throw CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED)

        val account = attributeMap(oAuth2User.getAttribute<Any>("kakao_account"))
        val profile = attributeMap(account["profile"])

        return KakaoUserInfo(
            id.toString(),
            stringValue(account["email"]),
            stringValue(profile["nickname"]),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun attributeMap(attribute: Any?): Map<String, Any> =
        if (attribute is Map<*, *>) attribute as Map<String, Any> else emptyMap()

    private fun stringValue(value: Any?): String? = value?.toString()
}
