package com.example.mysqlredisbenchmark.repository

import com.example.mysqlredisbenchmark.model.RankingEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RankingRepository : JpaRepository<RankingEntity, String> {

    // Returns a List to bypass the JPA count query overhead and measure only index scan + offset.
    @Query("SELECT r FROM RankingEntity r ORDER BY r.score DESC")
    fun findTopRankings(pageable: Pageable): List<RankingEntity>
}
