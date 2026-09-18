package iter.common.exception

import iter.common.response.ErrorResponse
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

// Bean Validation 애노테이션 속성 중 프론트에 의미 없는 것들 — params에서 제외
private val EXCLUDED_CONSTRAINT_ATTRIBUTES = setOf("message", "groups", "payload")

// API 명세의 { "code": "...", "message": "..." } 오류 형식을 적용한다.
// (기획서 DoD "예외 상황(존재하지 않는 id, 소유권 없는 접근 등) 핸들링 적용" 대응)
//
// code/message는 그대로 백엔드 한국어 문구다(로그/개발자용, 프론트 표시용 아님) — 실제 번역은 프론트가
// code(ErrorCode.name())를 키로 자체 사전에서 담당한다. @Valid 검증 실패는 한 줄 message로 뭉개지 않고
// errors(필드별 constraint + params) 구조로도 같이 내려줘서, 프론트가 "Size, {max: 100}" 같은 걸로
// 자체 문구를 조립할 수 있게 한다.
@RestControllerAdvice
class GlobalExceptionHandler {

    // 도메인에서 의도적으로 던진 비즈니스 예외
    @ExceptionHandler(CustomException::class)
    fun handleCustomException(e: CustomException): ResponseEntity<ErrorResponse> {
        val errorCode = e.errorCode
        log.warn("CustomException: {} - {}", errorCode, e.message)
        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse.from(errorCode.name, e.message!!))
    }

    // @Valid 검증 실패 (요청 DTO의 @NotNull, @NotBlank 등)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = e.bindingResult.fieldErrors.map { toFieldError(it) }
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: ErrorCode.VALIDATION_ERROR.message
        log.warn("Validation 실패: {}", message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.validation(ErrorCode.VALIDATION_ERROR.name, message, errors))
    }

    // @RequestParam, @PathVariable 등에 붙은 제약조건 위반
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(e: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        log.warn("ConstraintViolation: {}", e.message)
        val errors = e.constraintViolations.map { toFieldError(it) }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse.validation(
                    ErrorCode.VALIDATION_ERROR.name,
                    ErrorCode.VALIDATION_ERROR.message,
                    errors,
                ),
            )
    }

    // MethodArgumentNotValidException의 FieldError는 @Valid/Hibernate Validator를 거쳤으면 내부적으로
    // ConstraintViolation을 감싸고 있다 — unwrap으로 그걸 꺼내서 constraint 애노테이션 이름과 속성을 얻는다.
    // 이 프로젝트엔 커스텀 Spring Validator가 없어서 항상 성공하지만, 혹시 Bean Validation을 거치지 않은
    // FieldError(예: errors.rejectValue 직접 호출)가 생기면 unwrap이 실패하므로 대비해서 폴백한다.
    private fun toFieldError(fieldError: FieldError): ErrorResponse.FieldError {
        return try {
            val violation = fieldError.unwrap(ConstraintViolation::class.java)
            toFieldError(fieldError.field, violation)
        } catch (e: IllegalArgumentException) {
            ErrorResponse.FieldError(fieldError.field, fieldError.code ?: "", emptyMap())
        }
    }

    private fun toFieldError(violation: ConstraintViolation<*>): ErrorResponse.FieldError {
        val field = violation.propertyPath.toString()
        return toFieldError(field, violation)
    }

    private fun toFieldError(field: String, violation: ConstraintViolation<*>): ErrorResponse.FieldError {
        val descriptor = violation.constraintDescriptor
        val constraint = descriptor.annotation.annotationClass.java.simpleName
        val params = descriptor.attributes
            .filterKeys { it !in EXCLUDED_CONSTRAINT_ATTRIBUTES }
        return ErrorResponse.FieldError(field, constraint, params)
    }

    // @ModelAttribute 바인딩 실패 또는 enum 등 요청 파라미터 타입 변환 실패
    @ExceptionHandler(BindException::class, MethodArgumentTypeMismatchException::class)
    fun handleRequestBindingException(e: Exception): ResponseEntity<ErrorResponse> {
        log.warn("요청 파라미터 바인딩 실패: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.from(ErrorCode.VALIDATION_ERROR.name, ErrorCode.VALIDATION_ERROR.message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.warn("요청 본문을 읽을 수 없음: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.from(ErrorCode.VALIDATION_ERROR.name, ErrorCode.VALIDATION_ERROR.message))
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleHttpMediaTypeNotSupportedException(
        e: HttpMediaTypeNotSupportedException,
    ): ResponseEntity<ErrorResponse> {
        log.warn("지원하지 않는 Content-Type: {}", e.contentType)
        return ResponseEntity
            .status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.status)
            .body(
                ErrorResponse.from(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE.name,
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE.message,
                ),
            )
    }

    // 인가 실패 (소유권 없음, 권한 부족 등 — @PreAuthorize에서 발생)
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
        log.warn("AccessDenied: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.from(ErrorCode.FORBIDDEN.name, ErrorCode.FORBIDDEN.message))
    }

    // 같은 행을 동시에 수정해서 발생한 낙관적 락(@Version) 충돌 — 서버 오류가 아니라
    // "먼저 처리된 다른 요청과 경합했다"는 신호이므로 500이 아니라 409로 내려준다.
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailureException(
        e: ObjectOptimisticLockingFailureException,
    ): ResponseEntity<ErrorResponse> {
        log.warn("낙관적 락 충돌: {}", e.message)
        return ResponseEntity
            .status(ErrorCode.CONCURRENT_MODIFICATION.status)
            .body(
                ErrorResponse.from(
                    ErrorCode.CONCURRENT_MODIFICATION.name,
                    ErrorCode.CONCURRENT_MODIFICATION.message,
                ),
            )
    }

    // 그 외 예상하지 못한 모든 예외
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("예상하지 못한 예외 발생", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR.name, ErrorCode.INTERNAL_SERVER_ERROR.message))
    }
}
