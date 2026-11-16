package com.alkkuni.batch.raw

import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@Testcontainers
class S3RawStoreTest {
    companion object {
        // 최신 태그는 인증 토큰을 요구할 수 있어 버전을 고정한다.
        @Container
        val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:4.9"))

        private val s3: S3Client by lazy {
            S3Client.builder()
                .endpointOverride(localstack.endpoint)
                .region(Region.of(localstack.region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey)),
                )
                .forcePathStyle(true)
                .build()
        }
    }

    @Test
    fun `원본을 raw 아래 서비스·수집 시각·이름 키로 그대로 올린다`() {
        s3.createBucket { it.bucket("raw-ok") }
        val body = """{"I0030":{"row":[{"PRDLST_NM":"비타민C 1000"}]}}"""

        S3RawStore(s3, "raw-ok").save("I0030", LocalDateTime.of(2026, 11, 16, 3, 0, 5), "page_001.json", body)

        val saved = s3.getObjectAsBytes { it.bucket("raw-ok").key("raw/I0030/20261116T030005/page_001.json") }
        assertEquals(body, saved.asUtf8String())
    }

    @Test
    fun `올리기에 실패하면 예외로 알린다(원본 없이 적재를 이어가지 않게)`() {
        assertFailsWith<NoSuchBucketException> {
            S3RawStore(s3, "missing-bucket").save("I0030", LocalDateTime.of(2026, 11, 16, 3, 0), "page_001.json", "{}")
        }
    }

    @Test
    fun `설정값으로 구현을 고른다(기본은 로컬)`() {
        val runner = ApplicationContextRunner()
            .withUserConfiguration(LocalRawStore::class.java, S3RawStoreConfig::class.java)
            .withPropertyValues("alkkuni.raw-store.dir=${Files.createTempDirectory("raw")}")

        runner.run { assertIs<LocalRawStore>(it.getBean(RawStore::class.java)) }
        runner.withPropertyValues("alkkuni.raw-store.type=s3", "alkkuni.raw-store.bucket=b")
            .run { assertIs<S3RawStore>(it.getBean(RawStore::class.java)) }
        // 버킷이 비어 있으면 기동에서 실패한다(수집을 시작한 뒤가 아니라)
        runner.withPropertyValues("alkkuni.raw-store.type=s3", "alkkuni.raw-store.bucket=")
            .run { assertNotNull(it.startupFailure) }
    }
}
