package com.alkkuni.batch.raw

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.time.LocalDateTime

/**
 * S3에 저장한다(운영). 키: raw/{service}/{수집 시각}/{name}
 * 수집 시각이 키에 들어가 실행마다 키가 달라서 지난 원본을 덮어쓰지 않는다.
 * 재시도는 SDK 기본값(일시 오류 몇 번)에 맡기고, 그래도 실패하면 예외가 그대로 올라가 잡이 실패로 끝난다.
 */
class S3RawStore(private val s3: S3Client, private val bucket: String) : RawStore {

    override fun save(service: String, collectedAt: LocalDateTime, name: String, body: String) {
        s3.putObject(
            { it.bucket(bucket).key("raw/" + rawPath(service, collectedAt, name)).contentType("application/json; charset=utf-8") },
            RequestBody.fromString(body),
        )
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("alkkuni.raw-store.type", havingValue = "s3")
class S3RawStoreConfig {

    // 자격 증명은 SDK 기본 체인이 찾는다. EC2에서는 인스턴스 역할(alkkuni-ec2)이라 키를 따로 두지 않는다.
    // 컨테이너에서 인스턴스 역할을 받으려면 EC2 IMDSv2 hop limit이 2 이상이어야 한다(Docker 브리지 네트워크가 한 홉 더한다).
    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .region(Region.AP_NORTHEAST_2)
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .build()

    // 버킷이 비어 있으면 식약처 API를 부르기 전에, 기동할 때 멈춘다.
    @Bean
    fun rawStore(s3: S3Client, @Value("\${alkkuni.raw-store.bucket}") bucket: String): RawStore {
        require(bucket.isNotBlank()) { "alkkuni.raw-store.type=s3이면 alkkuni.raw-store.bucket(RAW_STORE_BUCKET)이 필요하다" }
        return S3RawStore(s3, bucket)
    }
}
