package com.example.mysqlredisbenchmark.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement

@Repository
class JdbcRankingRepository(private val jdbcTemplate: JdbcTemplate) {

    fun saveBatch(data: List<Pair<String, Int>>) {
        val chunkSize = 5000
        for (i in data.indices step chunkSize) {
            val chunk = data.subList(i, minOf(i + chunkSize, data.size))
            jdbcTemplate.batchUpdate(
                "INSERT INTO ranking (user_id, score) VALUES (?, ?)",
                chunk,
                chunk.size
            ) { ps: PreparedStatement, argument: Pair<String, Int> ->
                ps.setString(1, argument.first)
                ps.setInt(2, argument.second)
            }
        }
    }

    fun truncateTable() {
        jdbcTemplate.update("TRUNCATE TABLE ranking")
    }
}
