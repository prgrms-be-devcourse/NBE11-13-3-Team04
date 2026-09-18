package iter.reservation.domain.entity

// 같은 대여에서도 수령 사진과 반납 사진의 업로드 권한을 분리합니다.
enum class EvidenceUploadPhase {
    RECEIPT,
    RETURN
}
