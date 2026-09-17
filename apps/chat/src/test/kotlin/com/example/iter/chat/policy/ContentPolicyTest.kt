package com.example.iter.chat.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContentPolicyTest {

    @Test
    fun `평범한 문의는 마스킹되지 않는다`() {
        val result = ContentPolicy.apply("이 드릴 아직 대여 가능한가요?")

        assertThat(result.masked).isFalse()
        assertThat(result.violations).isEmpty()
        assertThat(result.maskedContent).isEqualTo("이 드릴 아직 대여 가능한가요?")
    }

    @Test
    fun `일반적인 전화번호 표기를 차단하고 원문 숫자를 남기지 않는다`() {
        val result = ContentPolicy.apply("연락처는 010-1234-5678 입니다")

        assertThat(result.masked).isTrue()
        assertThat(result.violations).containsExactly(ViolationCategory.PHONE)
        assertThat(result.maskedContent).isEqualTo("연락처는 [전화번호 차단] 입니다")
        assertThat(result.maskedContent).doesNotContain("1234", "5678")
    }

    @Test
    fun `공백으로 띈 전화번호도 잡는다`() {
        val result = ContentPolicy.apply("010 1234 5678로 연락주세요")

        assertThat(result.violations).containsExactly(ViolationCategory.PHONE)
    }

    @Test
    fun `구분자 없이 이어붙인 전화번호도 잡는다`() {
        val result = ContentPolicy.apply("01012345678 여기로 문자주세요")

        assertThat(result.violations).containsExactly(ViolationCategory.PHONE)
    }

    @Test
    fun `전각 숫자로 우회해도 잡는다`() {
        val result = ContentPolicy.apply("０１０１２３４５６７８로 주세요")

        assertThat(result.violations).containsExactly(ViolationCategory.PHONE)
    }

    @Test
    fun `한글 수사로 우회해도 잡는다`() {
        val result = ContentPolicy.apply("공일공 일이삼사 오육칠팔로 주세요")

        assertThat(result.violations).containsExactly(ViolationCategory.PHONE)
    }

    @Test
    fun `연속 10자리 이상 숫자는 계좌번호로 잡되 전화번호와 중복 표시하지 않는다`() {
        val result = ContentPolicy.apply("계좌 123456789012로 보내주세요")

        assertThat(result.violations).containsExactly(ViolationCategory.ACCOUNT)
    }

    @Test
    fun `카카오톡 아이디 문의를 잡는다`() {
        val result = ContentPolicy.apply("카톡 아이디로 연락주세요 abc123")

        assertThat(result.violations).contains(ViolationCategory.MESSENGER_ID)
    }

    @Test
    fun `외부 링크를 잡는다`() {
        val result = ContentPolicy.apply("자세한 건 https://example.com/detail 참고하세요")

        assertThat(result.violations).contains(ViolationCategory.EXTERNAL_LINK)
        assertThat(result.maskedContent).doesNotContain("example.com")
    }

    @Test
    fun `직거래 유도 표현을 잡는다`() {
        val result = ContentPolicy.apply("현금으로 직접 만나서 깎아 드릴게요")

        assertThat(result.violations).contains(ViolationCategory.DIRECT_DEAL)
    }

    @Test
    fun `서로 붙어 있는 위반 구간은 라벨을 하나로 합친다`() {
        // 전화번호 바로 뒤에 붙는 "카톡"이 따로따로 마스킹되면 라벨이 이어 붙어 보인다 —
        // 병합해서 하나의 마스킹 블록으로 만든다.
        val result = ContentPolicy.apply("01012345678카톡")

        assertThat(result.maskedContent).isEqualTo("[전화번호/메신저 ID 차단]")
    }

    @Test
    fun `여러 위반이 있으면 카테고리를 전부 반환한다`() {
        val result = ContentPolicy.apply("010-1234-5678로 현금 직거래 원해요")

        assertThat(result.violations).containsExactlyInAnyOrder(ViolationCategory.PHONE, ViolationCategory.DIRECT_DEAL)
    }
}
