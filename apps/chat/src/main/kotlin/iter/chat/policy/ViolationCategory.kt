package iter.chat.policy

// chat_violations.category에는 이 enum의 name()이 문자열로 저장된다(Violation.category
// 참고) — 값을 바꾸면 기존 기록과 어긋나니 추가만 하고 이름은 바꾸지 않는다.
enum class ViolationCategory(val label: String) {
    PHONE("전화번호"),
    ACCOUNT("계좌번호"),
    MESSENGER_ID("메신저 ID"),
    EXTERNAL_LINK("외부 링크"),
    DIRECT_DEAL("직거래 유도"),
}
