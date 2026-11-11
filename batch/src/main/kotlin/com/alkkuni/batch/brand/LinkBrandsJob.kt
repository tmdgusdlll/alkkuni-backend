package com.alkkuni.batch.brand

import com.alkkuni.batch.JobLock
import com.alkkuni.batch.product.InitialLoadJob
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 브랜드 사전을 고친 뒤 기존 제품의 brand_id를 다시 맞춘다. 새로 적재·갱신되는 제품은 ProductUpserter가 연결한다.
 * 수집 작업도 제품을 쓰므로 같은 잠금(I0030)으로 겹치지 않게 한다.
 * `alkkuni.job=link-brands`로 띄우면 한 번 돌고 끝난다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "link-brands")
class LinkBrandsJob(
    private val linker: BrandLinker,
    private val jobLock: JobLock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val result = jobLock.withLock(InitialLoadJob.SERVICE) { linker.link() }
        val rate = if (result.target == 0L) 0.0 else result.linked * 100.0 / result.target
        log.info(
            "브랜드 연결 완료: 바뀐 제품 {}건, 원료 제외 제품 {}건 중 {}건 연결({}%)",
            result.relinked, result.target, result.linked, "%.1f".format(rate),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(LinkBrandsJob::class.java)
    }
}
