package iter.ai.port

import iter.ai.service.ReportAnalysisContext

data class ReportAnalysisTarget(val targetType: String, val status: String, val closed: Boolean)

// 신고 AI가 auth·dispute와 증빙 도메인의 구현을 직접 참조하지 않게 하는 출력 Port입니다.
interface ReportAnalysisContextPort {
    // 저장된 분석 작업을 조회하기 전에 대상 신고가 실제로 존재하는지 확인합니다.
    fun requireReport(reportId: Long)

    // 외부 증빙 조회 전에 관리자 권한과 신고 종결 여부를 확인합니다.
    fun requireAdminAndReport(adminId: Long, reportId: Long): ReportAnalysisTarget

    // 호출 트랜잭션 안에서 관리자·신고 행을 잠그고 현재 상태를 반환합니다.
    fun lockAdminAndReport(adminId: Long, reportId: Long): ReportAnalysisTarget

    // 신고 유형별 시스템 사실·공개 텍스트·증빙 사진을 AI 입력 컨텍스트로 조합합니다.
    fun build(reportId: Long, description: String): ReportAnalysisContext
}
