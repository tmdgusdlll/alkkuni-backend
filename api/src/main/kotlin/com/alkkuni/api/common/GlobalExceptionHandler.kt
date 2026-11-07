package com.alkkuni.api.common

import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.method.ParameterErrors
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import tools.jackson.core.JacksonException

/**
 * 예외를 ApiResponse 에러 형식으로 바꾼다.
 * - Spring MVC 내장 예외(검증 실패, 필수 파라미터 누락, 타입 불일치, 없는 경로, 지원하지 않는 메서드 등)는
 *   ResponseEntityExceptionHandler가 HTTP 상태를 정하고, 여기서는 본문만 바꾼다(handleExceptionInternal).
 * - 그 밖의 예외는 500. 내부 메시지는 응답에 넣지 않고 로그에 남긴다.
 * DispatcherServlet 밖(필터)에서 난 에러는 여기를 거치지 않고 Spring Boot 기본 /error 형식으로 나간다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    // 본문만 넘기고 나머지는 부모에 맡긴다. 부모가 이미 응답이 나간 경우(committed)는 쓰지 않고,
    // 500이면 요청에 예외를 남겨 http.server.requests 지표의 exception 태그가 채워진다.
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val code = ErrorCode.from(statusCode.value())
        if (code == ErrorCode.INTERNAL_ERROR) log.error("요청 처리 실패", ex)
        return super.handleExceptionInternal(ex, ApiResponse.error(code, details(ex)), headers, statusCode, request)
    }

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(ex.code.status).body(ApiResponse.error(ex.code, ex.details))

    // ponytail: @ResponseStatus 예외·Spring Security 예외도 여기서 500이 된다. 인증(3단계)을 넣을 때 따로 처리한다.
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("요청 처리 실패", ex)
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status).body(ApiResponse.error(ErrorCode.INTERNAL_ERROR))
    }

    // 어느 입력이 틀렸는지 알 수 있는 예외만 details를 채운다.
    private fun details(ex: Exception): List<ErrorDetail>? = when (ex) {
        // @RequestBody 검증 실패. 클래스 단위 제약(필드가 없는 오류)은 객체 이름으로 남긴다.
        is MethodArgumentNotValidException -> ex.bindingResult.allErrors.map { it.toDetail() }
        // 메서드 파라미터 검증. 다른 파라미터에 제약이 있으면 @Valid 본문 오류도 여기로 오는데, 그때는 본문 필드 경로를 쓴다.
        is HandlerMethodValidationException -> ex.parameterValidationResults.flatMap { result ->
            if (result is ParameterErrors) {
                result.allErrors.map { it.toDetail() }
            } else {
                result.resolvableErrors.map { ErrorDetail(result.methodParameter.parameterName ?: "", it.defaultMessage ?: "") }
            }
        }
        is MissingServletRequestParameterException -> listOf(ErrorDetail(ex.parameterName, "필수 값입니다."))
        is TypeMismatchException -> listOf(ErrorDetail(ex.propertyName ?: "", "형식이 올바르지 않습니다."))
        // JSON 본문의 null 아닌 필드 누락·타입 오류. Jackson이 어느 필드에서 실패했는지 경로를 남긴다.
        is HttpMessageNotReadableException -> (ex.cause as? JacksonException)?.path
            ?.mapNotNull { it.propertyName }?.takeIf { it.isNotEmpty() }
            ?.let { listOf(ErrorDetail(it.joinToString("."), "값이 없거나 형식이 올바르지 않습니다.")) }
        else -> null
    }

    private fun ObjectError.toDetail() = ErrorDetail((this as? FieldError)?.field ?: objectName, defaultMessage ?: "")

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
