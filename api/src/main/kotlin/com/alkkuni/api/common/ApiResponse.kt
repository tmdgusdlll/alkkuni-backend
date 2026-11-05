package com.alkkuni.api.common

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 모든 API 응답의 바깥 형식. 성공이면 data, 실패면 error 하나만 담는다.
 * 성공: {"data": ...}  실패: {"error": {"code": "INVALID_INPUT", "message": "...", "details": [...]}}
 * 컨트롤러가 직접 감싸서 돌려준다. actuator(/actuator/health)는 감싸지 않는다(배포 헬스 체크가 기본 형식을 읽는다).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val data: T? = null,
    val error: ErrorBody? = null,
) {
    companion object {
        fun <T> ok(data: T) = ApiResponse(data = data)

        fun error(code: ErrorCode, details: List<ErrorDetail>? = null) =
            ApiResponse<Nothing>(error = ErrorBody(code.name, code.message, details))
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorBody(
    val code: String,
    val message: String,
    val details: List<ErrorDetail>? = null,
)

// 어느 입력값이 왜 틀렸는지. field는 요청 파라미터 이름이나 본문 필드 경로다.
data class ErrorDetail(
    val field: String,
    val reason: String,
)
