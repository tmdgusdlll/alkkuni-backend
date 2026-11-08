package com.alkkuni.api.common

import org.springframework.http.CacheControl
import java.time.Duration

// 공개 조회 API의 성공 응답 캐시(PLANNING.md "캐시"). 에러 응답은 GlobalExceptionHandler가 만들어 붙지 않는다.
// 식약처 데이터가 하루 한 번 바뀌어 60초 지연은 문제없다.
val PUBLIC_CACHE: CacheControl = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()
