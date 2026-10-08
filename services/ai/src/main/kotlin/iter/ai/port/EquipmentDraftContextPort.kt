package iter.ai.port

data class DraftImageReference(
    val imageId: String,
    val objectKey: String,
    val etag: String,
    val captureSlot: String,
    val contentType: String,
    val sizeBytes: Long
) {
    fun toPayload(): Map<String, Any> = mapOf(
        "imageId" to imageId,
        "objectKey" to objectKey,
        "etag" to etag,
        "captureSlot" to captureSlot,
        "contentType" to contentType,
        "sizeBytes" to sizeBytes
    )
}

// AI 모듈과 회원·장비 업로드 구현 사이의 경계를 정의하는 출력 Port입니다.
interface EquipmentDraftContextPort {
    // S3 조회를 포함하므로 AI 작업 저장 트랜잭션 밖에서 호출합니다.
    fun loadValidatedImages(ownerId: Long, objectKeys: List<String>): List<DraftImageReference>

    // 저장된 작업 조회 시 요청자가 현재 AI 기능을 사용할 수 있는 회원인지 확인합니다.
    fun requireActiveOwner(ownerId: Long)

    // 호출 트랜잭션 안에서 회원 행을 잠그고 업로드 소유권·유효성을 다시 확인합니다.
    fun lockOwnerAndValidateUploads(ownerId: Long, objectKeys: List<String>)
}
