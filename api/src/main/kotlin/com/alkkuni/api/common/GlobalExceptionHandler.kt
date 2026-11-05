package com.alkkuni.api.common

import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.validation.method.ParameterValidationResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 예외를 ApiResponse 에러 형식으로 바꾼다.
 * - Spring MVC 내장 예외(검증 실패, 필수 파라미터 누락, 타입 불일치, 없는 경로, 지원하지 않는 메서드 등)는
 *   ResponseEntityExceptionHandler가 HTTP 상태를 정하고, 여기서는 본문만 바꾼다(handleExceptionInternal).
 * - 그 밖의 예외는 500. 내부 메시지는 응답에 넣지 않고 로그에 남긴다.
 * DispatcherServlet 밖(필터)에서 난 에러는 여기를 거치지 않고 Spring Boot 기본 /error 형식으로 나간다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val code = ErrorCode.from(statusCode.value())
        if (code == ErrorCode.INTERNAL_ERROR) log.error("요청 처리 실패", ex)
        return ResponseEntity.status(statusCode).headers(headers).body(ApiResponse.error(code, details(ex)))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("요청 처리 실패", ex)
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status).body(ApiResponse.error(ErrorCode.INTERNAL_ERROR))
    }

    // 어느 입력이 틀렸는지 알 수 있는 예외만 details를 채운다.
    private fun details(ex: Exception): List<ErrorDetail>? = when (ex) {
        // @RequestBody 검증 실패
        is MethodArgumentNotValidException -> ex.bindingResult.fieldErrors.map { ErrorDetail(it.field, it.defaultMessage ?: "") }
        // @RequestParam 등 메서드 파라미터 제약(@Size 등) 위반
        is HandlerMethodValidationException -> ex.parameterValidationResults.flatMap { result ->
            result.resolvableErrors.map { ErrorDetail(fieldName(result), it.defaultMessage ?: "") }
        }
        is MissingServletRequestParameterException -> listOf(ErrorDetail(ex.parameterName, "필수 값입니다."))
        is TypeMismatchException -> listOf(ErrorDetail(ex.propertyName ?: "", "형식이 올바르지 않습니다."))
        else -> null
    }

    private fun fieldName(result: ParameterValidationResult): String =
        result.methodParameter.parameterName ?: ""

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
