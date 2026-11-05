package com.alkkuni.api.common

import org.springframework.http.HttpStatus

/**
 * 에러 코드. 이름은 대문자 스네이크, 공통 코드는 접두어 없이 두고 도메인 코드는 PRODUCT_NOT_FOUND처럼 도메인 이름을 앞에 붙인다.
 * 코드 이름은 프론트가 분기에 쓰므로 바꾸거나 지우지 않는다(REVIEW.md: 응답 필드 호환).
 * message는 사용자에게 보여도 되는 문장만 쓴다. 내부 예외 메시지는 응답에 넣지 않고 로그에만 남긴다.
 */
enum class ErrorCode(val status: HttpStatus, val message: String) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    ;

    companion object {
        // Spring 내장 예외의 HTTP 상태를 코드로 바꾼다. 따로 코드가 없는 4xx(415 등)는 잘못된 요청으로 본다.
        fun from(status: Int): ErrorCode = when {
            status == 404 -> NOT_FOUND
            status == 405 -> METHOD_NOT_ALLOWED
            status >= 500 -> INTERNAL_ERROR
            else -> INVALID_INPUT
        }
    }
}
