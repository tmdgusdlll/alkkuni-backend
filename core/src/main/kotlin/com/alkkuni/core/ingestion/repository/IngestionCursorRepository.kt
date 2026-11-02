package com.alkkuni.core.ingestion.repository

import com.alkkuni.core.ingestion.entity.IngestionCursor
import org.springframework.data.jpa.repository.JpaRepository

// id는 소스 이름(I0030 등). 갱신은 같은 id로 새 객체를 save하면 merge로 덮어쓴다.
interface IngestionCursorRepository : JpaRepository<IngestionCursor, String>
