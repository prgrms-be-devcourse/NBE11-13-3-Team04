package com.example.iter.dispute.api

// dispute 가 다른 도메인에게 공개하는 신고 조회 창구.
// 규약은 auth/api/UserQueryPort 의 주석을 따른다.
interface ReportQueryPort {

    // 아직 처리되지 않은(접수 상태) 신고 건수.
    //
    // 호출부에 ReportStatus 를 노출하지 않으려고 상태를 인자로 받지 않는다.
    // "무엇이 미처리 상태인가"는 dispute 의 지식이고, 상태가 하나 늘어나도
    // 관리자 대시보드 코드는 고치지 않아야 한다.
    fun countReceived(): Long

    // 이 회원을 대상으로 접수된 신고 건수. 상태로 거르지 않는다.
    //
    // 호출부에 ReportTargetType 을 노출하지 않으려고 대상 종류를 인자로 받지 않는다.
    fun countAgainstUser(userId: Long): Long
}
