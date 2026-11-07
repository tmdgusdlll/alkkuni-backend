package com.alkkuni.api.common

// 서비스가 의도해서 돌려주는 에러. GlobalExceptionHandler가 code의 HTTP 상태와 메시지로 응답한다.
class ApiException(
    val code: ErrorCode,
    val details: List<ErrorDetail>? = null,
) : RuntimeException(code.message)
