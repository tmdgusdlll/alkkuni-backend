package com.alkkuni.core.recall.repository

import com.alkkuni.core.recall.entity.Recall
import org.springframework.data.jpa.repository.JpaRepository

interface RecallRepository : JpaRepository<Recall, Long>
