package com.alkkuni.batch

import org.springframework.stereotype.Component
import java.sql.Connection
import javax.sql.DataSource

/**
 * 같은 출처를 다루는 배치가 겹쳐 돌지 않게 PostgreSQL advisory lock을 잡는다.
 * 초기 적재와 증분이 동시에 돌면 같은 신고번호를 둘 다 INSERT하다 UNIQUE 위반으로 실패하고, 커서도 엇갈린다.
 *
 * 세션 잠금이라 작업이 끝날 때까지 연결 하나를 붙잡는다. 프로세스가 죽으면 연결이 끊기며 잠금도 풀린다.
 * 잠금을 못 잡으면 기다리지 않고 예외로 끝낸다. 다음 cron 실행이 다시 시도한다.
 */
@Component
class JobLock(private val dataSource: DataSource) {

    fun <T> withLock(key: String, block: () -> T): T =
        dataSource.connection.use { conn ->
            check(query(conn, "select pg_try_advisory_lock(hashtext(?))", key)) {
                "$key 작업이 이미 실행 중이다. 끝난 뒤 다시 돌린다."
            }
            try {
                block()
            } finally {
                query(conn, "select pg_advisory_unlock(hashtext(?))", key)
            }
        }

    private fun query(conn: Connection, sql: String, key: String): Boolean =
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        }
}
