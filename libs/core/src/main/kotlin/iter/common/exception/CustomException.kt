package iter.common.exception

// 비즈니스 예외를 나타내는 공통 예외 클래스.
// ErrorCode 하나만 넘기면 GlobalExceptionHandler가 알아서 상태 코드/메시지를 응답으로 변환한다.
// 사용 예: throw CustomException(ErrorCode.EMAIL_ALREADY_EXISTS)
open class CustomException @JvmOverloads constructor(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
) : RuntimeException(message)
