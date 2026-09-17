package com.example.iter.auth.dto.request

import com.example.iter.auth.api.PreferredLanguage
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// record가 아니라 일반 클래스인 이유: Jackson이 "필드가 아예 없음"과 "필드가 null로 옴"을
// 구분해야 한다(부분 수정 — 보낸 필드만 반영). @JsonSetter(nulls = FAIL)이 후자를 예외로
// 막고, 필드가 아예 없으면 세터 자체가 안 불려서 null로 남는다. 접근자 이름이 getX()가
// 아니라 record 스타일(name(), nickname()...)인 건 자바 시절 그대로 유지한 것 — 검증
// 애너테이션은 필드에 직접 붙어 있어(코틀린도 동일) 이 접근자 이름과 무관하게 동작한다.
// 코틀린 프로퍼티(var name 등)로 선언하면 자동 생성되는 getName()/setName(String?)이
// 아래 수동 setName(String)/name()과 JVM 시그니처가 겹쳐 컴파일이 깨지므로, 백킹 필드
// 이름을 별도(nameValue 등)로 둬서 충돌을 피한다.
class UserUpdateRequest {

    @Size(min = 1, max = 20, message = "이름은 1자 이상 20자 이하여야 합니다.")
    @Pattern(regexp = ".*\\S.*", message = "이름은 공백만 입력할 수 없습니다.")
    private var nameValue: String? = null

    @Size(min = 1, max = 20, message = "닉네임은 1자 이상 20자 이하여야 합니다.")
    @Pattern(regexp = ".*\\S.*", message = "닉네임은 공백만 입력할 수 없습니다.")
    private var nicknameValue: String? = null

    @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
    private var phoneValue: String? = null

    // 이메일 등 백엔드가 직접 언어를 확정해서 보내야 하는 콘텐츠(현재는 이메일)에 쓰인다.
    private var preferredLanguageValue: PreferredLanguage? = null

    @JsonSetter(value = "name", nulls = Nulls.FAIL)
    fun setName(name: String) {
        nameValue = name.trim()
    }

    @JsonSetter(value = "nickname", nulls = Nulls.FAIL)
    fun setNickname(nickname: String) {
        nicknameValue = nickname.trim()
    }

    @JsonSetter(value = "phone", nulls = Nulls.FAIL)
    fun setPhone(phone: String) {
        phoneValue = phone.trim()
    }

    @JsonSetter(value = "preferredLanguage", nulls = Nulls.FAIL)
    fun setPreferredLanguage(preferredLanguage: PreferredLanguage) {
        preferredLanguageValue = preferredLanguage
    }

    fun name(): String? = nameValue

    fun nickname(): String? = nicknameValue

    fun phone(): String? = phoneValue

    fun preferredLanguage(): PreferredLanguage? = preferredLanguageValue

    @AssertTrue(message = "변경할 회원 정보를 하나 이상 입력해주세요.")
    fun isAnyFieldPresent(): Boolean =
        nameValue != null || nicknameValue != null || phoneValue != null || preferredLanguageValue != null
}
