package com.alkkuni.batch.product

import com.alkkuni.batch.JobLock
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 카테고리 판정 규칙을 바꾸거나 V15를 처음 적용한 뒤 기존 제품을 다시 판정한다. 새로 적재·갱신되는 제품은 ProductUpserter가 쓴다.
 * 수집 작업도 제품을 쓰므로 같은 잠금(I0030)으로 겹치지 않게 한다.
 * `alkkuni.job=link-categories`로 띄우면 한 번 돌고 끝난다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "link-categories")
class LinkCategoriesJob(
    private val linker: CategoryLinker,
    private val jobLock: JobLock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val result = jobLock.withLock(InitialLoadJob.SERVICE) { linker.link() }
        log.info(
            "기능성 카테고리 판정 완료: 바뀐 행 {}건, 원료 제외 제품 {}건 중 {}건에 카테고리",
            result.changed, result.target, result.categorized,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(LinkCategoriesJob::class.java)
    }
}
