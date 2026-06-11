package com.example.mysqlredisbenchmark.service

import com.example.mysqlredisbenchmark.dto.CurvePoint
import com.example.mysqlredisbenchmark.dto.RankingBenchmarkResult
import com.example.mysqlredisbenchmark.dto.RankingCurveResult
import com.example.mysqlredisbenchmark.dto.RankingRecord
import com.example.mysqlredisbenchmark.repository.JdbcRankingRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.Random

@Service
class RankingBenchmarkService(
    private val jdbcRankingRepository: JdbcRankingRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val stringRedisTemplate: StringRedisTemplate
) {

    @Suppress("UNCHECKED_CAST")
    private val keySerializer = stringRedisTemplate.keySerializer as RedisSerializer<String>

    @Suppress("UNCHECKED_CAST")
    private val valueSerializer = stringRedisTemplate.valueSerializer as RedisSerializer<String>

    fun initializeRankingData(count: Int): String {
        val coercedCount = count.coerceIn(100, 1000000)
        
        // 1. Clean old data
        jdbcRankingRepository.truncateTable()
        stringRedisTemplate.delete("ranking")

        // 2. Generate random records
        val random = Random()
        val data = (1..coercedCount).map { i ->
            Pair("user_$i", random.nextInt(100_000_000))
        }

        // 3. Insert into MySQL (via JDBC Batch)
        jdbcRankingRepository.saveBatch(data)

        // 4. Insert into Redis ZSET (via Pipelined zAdd)
        val chunkSize = 10000
        for (i in data.indices step chunkSize) {
            val chunk = data.subList(i, minOf(i + chunkSize, data.size))
            stringRedisTemplate.executePipelined { connection ->
                chunk.forEach { (userId, score) ->
                    val rawKey = keySerializer.serialize("ranking")!!
                    val rawValue = valueSerializer.serialize(userId)!!
                    connection.zSetCommands().zAdd(rawKey, score.toDouble(), rawValue)
                }
                null
            }
        }

        return "성공적으로 $coercedCount 건의 랭킹 데이터를 MySQL과 Redis에 저장했습니다."
    }

    fun runRankingBenchmark(offset: Int, limit: Int): RankingBenchmarkResult {
        val coercedOffset = offset.coerceAtLeast(0)
        val coercedLimit = limit.coerceIn(1, 100)

        // 1. MySQL Benchmark
        val mysqlSql = "SELECT user_id, score FROM ranking ORDER BY score DESC LIMIT ? OFFSET ?"
        val mysqlStart = System.nanoTime()
        val mysqlRaw = jdbcTemplate.query(mysqlSql, { rs, _ ->
            Pair(rs.getString("user_id"), rs.getInt("score"))
        }, coercedLimit, coercedOffset)
        val mysqlEnd = System.nanoTime()
        val mysqlTimeMs = (mysqlEnd - mysqlStart) / 1_000_000.0

        // Map to DTO
        val mysqlResults = mysqlRaw.mapIndexed { i, pair ->
            RankingRecord(rank = coercedOffset + i + 1, userId = pair.first, score = pair.second)
        }

        // 2. Redis Benchmark
        val redisStart = System.nanoTime()
        val redisRaw = stringRedisTemplate.opsForZSet().reverseRangeWithScores(
            "ranking",
            coercedOffset.toLong(),
            (coercedOffset + coercedLimit - 1).toLong()
        )
        val redisEnd = System.nanoTime()
        val redisTimeMs = (redisEnd - redisStart) / 1_000_000.0

        // Map to DTO
        val redisResults = redisRaw?.mapIndexed { i, typedTuple ->
            RankingRecord(
                rank = coercedOffset + i + 1,
                userId = typedTuple.value ?: "",
                score = typedTuple.score?.toInt() ?: 0
            )
        } ?: emptyList()

        return RankingBenchmarkResult(
            offset = coercedOffset,
            limit = coercedLimit,
            mysqlTimeMs = mysqlTimeMs,
            redisTimeMs = redisTimeMs,
            mysqlResults = mysqlResults,
            redisResults = redisResults
        )
    }

    fun runCurveBenchmark(limit: Int): RankingCurveResult {
        val coercedLimit = limit.coerceIn(1, 100)

        // Find current database size
        val totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ranking", Int::class.java) ?: 0

        // Define a list of offsets to test based on database size
        val baseOffsets = listOf(0, 100, 1000, 5000, 10000, 30000, 50000, 100000, 200000, 500000, 900000)
        val testOffsets = baseOffsets.filter { it < totalCount && (it + coercedLimit) <= totalCount }.toMutableList()
        
        // Add the max possible offset if not already included
        val maxOffset = totalCount - coercedLimit
        if (maxOffset > 0 && !testOffsets.contains(maxOffset)) {
            testOffsets.add(maxOffset)
        }
        
        testOffsets.sort()

        val points = testOffsets.map { offset ->
            // Run MySQL query (timing only)
            val mysqlSql = "SELECT user_id, score FROM ranking ORDER BY score DESC LIMIT ? OFFSET ?"
            val mysqlStart = System.nanoTime()
            jdbcTemplate.query(mysqlSql, { _, _ -> null }, coercedLimit, offset)
            val mysqlEnd = System.nanoTime()
            val mysqlTimeMs = (mysqlEnd - mysqlStart) / 1_000_000.0

            // Run Redis query (timing only)
            val redisStart = System.nanoTime()
            stringRedisTemplate.opsForZSet().reverseRangeWithScores(
                "ranking",
                offset.toLong(),
                (offset + coercedLimit - 1).toLong()
            )
            val redisEnd = System.nanoTime()
            val redisTimeMs = (redisEnd - redisStart) / 1_000_000.0

            CurvePoint(
                offset = offset,
                mysqlTimeMs = mysqlTimeMs,
                redisTimeMs = redisTimeMs
            )
        }

        return RankingCurveResult(
            limit = coercedLimit,
            points = points
        )
    }
}
