package com.alkkuni.batch

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class BatchConfig {

    // 커서 날짜와 CHNG_DT는 한국 날짜 기준이다. 컨테이너 기본 시간대(UTC)에 기대면
    // 한국 00~09시 실행에서 오늘이 하루 전으로 계산돼 커서가 역행한다.
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
