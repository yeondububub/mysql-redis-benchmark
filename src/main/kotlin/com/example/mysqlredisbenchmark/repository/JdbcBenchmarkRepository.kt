package com.example.mysqlredisbenchmark.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement

@Repository
class JdbcBenchmarkRepository(private val jdbcTemplate: JdbcTemplate) {

    fun saveSingle(id: String, value: String) {
        jdbcTemplate.update(
            "INSERT INTO benchmark_data (id, val_data) VALUES (?, ?) ON DUPLICATE KEY UPDATE val_data = ?",
            id,
            value,
            value
        )
    }

    fun saveBatch(data: List<Pair<String, String>>) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO benchmark_data (id, val_data) VALUES (?, ?) ON DUPLICATE KEY UPDATE val_data = ?",
            data,
            data.size
        ) { ps: PreparedStatement, argument: Pair<String, String> ->
            ps.setString(1, argument.first)
            ps.setString(2, argument.second)
            ps.setString(3, argument.second)
        }
    }

    fun findById(id: String): String? {
        return try {
            jdbcTemplate.queryForObject("SELECT val_data FROM benchmark_data WHERE id = ?", String::class.java, id)
        } catch (e: Exception) {
            null
        }
    }

    fun findAllByIds(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT id, val_data FROM benchmark_data WHERE id IN ($placeholders)"
        val results = mutableMapOf<String, String>()
        jdbcTemplate.query(sql, { rs, _ ->
            results[rs.getString("id")] = rs.getString("val_data")
        }, *ids.toTypedArray())
        return results
    }

    fun deleteAll() {
        jdbcTemplate.update("TRUNCATE TABLE benchmark_data")
    }
}
