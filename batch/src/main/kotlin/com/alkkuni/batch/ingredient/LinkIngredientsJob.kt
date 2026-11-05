package com.alkkuni.batch.ingredient

import com.alkkuni.batch.JobLock
import com.alkkuni.batch.product.InitialLoadJob
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 성분 사전을 고친 뒤 기존 성분 행의 ingredient_id(와 IU 1일 함량)를 다시 맞춘다.
 * 판정 버전(VERSION)과 분리한 이유: 사전을 고칠 때마다 재적재하면 식약처 API 한도(1시간 100회)에 걸린다.
 *
 * 수집 작업도 product_ingredient를 지우고 다시 쓰므로 같은 잠금(I0030)으로 겹치지 않게 한다.
 * `alkkuni.job=link-ingredients`로 띄우면 한 번 돌고 끝난다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "link-ingredients")
class LinkIngredientsJob(
    private val linker: IngredientLinker,
    private val jobLock: JobLock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val result = jobLock.withLock(InitialLoadJob.SERVICE) { linker.link() }
        val rate = if (result.targetRows == 0L) 0.0 else result.linkedRows * 100.0 / result.targetRows
        log.info(
            "성분 연결 완료: 바뀐 행 {}건, IU 1일 함량 재계산 {}건, 원료 제외 성분 행 {}건 중 {}건 연결({}%)",
            result.relinkedRows, result.recalculatedIuRows, result.targetRows, result.linkedRows, "%.1f".format(rate),
        )
        log.info("연결 안 된 상위 키(행 수): {}", result.topUnlinked.joinToString { "${it.first}=${it.second}" })
    }

    companion object {
        private val log = LoggerFactory.getLogger(LinkIngredientsJob::class.java)
    }
}
