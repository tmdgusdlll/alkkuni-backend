package com.alkkuni.api.common

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 실제 DispatcherServlet·Jackson 설정으로 에러 응답 모양과 상태 코드를 확인한다. 컨트롤러는 이 테스트 전용이다.
@WebMvcTest(controllers = [GlobalExceptionHandlerTest.TestController::class])
// 테스트 안의 중첩 컨트롤러는 스캔되지 않아 직접 등록한다.
@Import(GlobalExceptionHandlerTest.TestController::class)
class GlobalExceptionHandlerTest(@Autowired private val mvc: MockMvc) {

    @RestController
    class TestController {
        @GetMapping("/test/ok")
        fun ok() = ApiResponse.ok(mapOf("name" to "루테인"))

        @GetMapping("/test/param")
        fun param(@RequestParam @Size(min = 2, message = "2자 이상 입력해 주세요.") q: String, @RequestParam page: Int = 0) =
            ApiResponse.ok(q)

        @PostMapping("/test/body")
        fun body(@Valid @RequestBody body: Body) = ApiResponse.ok(body.name ?: "")

        // 다른 파라미터에 제약이 있으면 Spring이 메서드 검증으로 본문 오류까지 한꺼번에 던진다
        @PostMapping("/test/body-and-param")
        fun bodyAndParam(@RequestParam @Size(min = 2) q: String, @Valid @RequestBody body: Body) = ApiResponse.ok(q)

        // Kotlin 관례대로 null 아닌 타입. 필드가 빠지면 검증 전에 Jackson이 실패한다
        @PostMapping("/test/non-null")
        fun nonNull(@RequestBody body: NonNullBody) = ApiResponse.ok(body.name)

        @GetMapping("/test/boom")
        fun boom(): ApiResponse<String> = throw IllegalStateException("DB 비밀번호가 틀림")
    }

    data class Body(@field:NotBlank(message = "필수 값입니다.") val name: String?)

    data class NonNullBody(val name: String, val count: Int)

    @Test
    fun `성공은 data만 담는다`() {
        mvc.get("/test/ok").andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("루테인") }
            jsonPath("$.error") { doesNotExist() }
        }
    }

    @Test
    fun `파라미터 제약 위반은 400 INVALID_INPUT이고 어느 값이 틀렸는지 알려준다`() {
        mvc.get("/test/param") { param("q", "a") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.data") { doesNotExist() }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
            jsonPath("$.error.details[0].field") { value("q") }
            jsonPath("$.error.details[0].reason") { value("2자 이상 입력해 주세요.") }
        }
    }

    @Test
    fun `필수 파라미터 누락과 타입 불일치도 400이다`() {
        mvc.get("/test/param").andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
            jsonPath("$.error.details[0].field") { value("q") }
        }
        mvc.get("/test/param") { param("q", "루테인"); param("page", "abc") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.details[0].field") { value("page") }
        }
    }

    @Test
    fun `본문 검증 실패는 필드 경로를 알려준다`() {
        mvc.post("/test/body") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": " "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.details[0].field") { value("name") }
            jsonPath("$.error.details[0].reason") { value("필수 값입니다.") }
        }
    }

    @Test
    fun `파라미터 제약과 본문 검증이 함께 실패해도 본문 오류는 필드 경로로 알려준다`() {
        mvc.post("/test/body-and-param") {
            param("q", "루테인")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": " "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.details[0].field") { value("name") }
        }
    }

    @Test
    fun `null 아닌 필드가 빠진 JSON은 어느 필드인지 알려준다`() {
        mvc.post("/test/non-null") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"count": 1}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
            jsonPath("$.error.details[0].field") { value("name") }
        }
        mvc.post("/test/non-null") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "a", "count": "많이"}"""
        }.andExpect {
            jsonPath("$.error.details[0].field") { value("count") }
        }
    }

    @Test
    fun `없는 경로는 404, 지원하지 않는 메서드는 405`() {
        mvc.get("/test/none").andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }
        mvc.post("/test/ok").andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.error.code") { value("METHOD_NOT_ALLOWED") }
        }
    }

    @Test
    fun `예상하지 못한 예외는 500이고 내부 메시지를 응답에 넣지 않는다`() {
        mvc.get("/test/boom").andExpect {
            status { isInternalServerError() }
            jsonPath("$.error.code") { value("INTERNAL_ERROR") }
            jsonPath("$.error.message") { value(ErrorCode.INTERNAL_ERROR.message) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("비밀번호"))) }
        }
    }
}
