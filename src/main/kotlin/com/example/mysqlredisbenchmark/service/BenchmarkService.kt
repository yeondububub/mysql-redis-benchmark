package com.example.mysqlredisbenchmark.service

import com.example.mysqlredisbenchmark.dto.BenchmarkMetric
import com.example.mysqlredisbenchmark.dto.BenchmarkRequest
import com.example.mysqlredisbenchmark.dto.BenchmarkResponse
import com.example.mysqlredisbenchmark.model.BenchmarkData
import com.example.mysqlredisbenchmark.repository.BenchmarkRepository
import com.example.mysqlredisbenchmark.repository.JdbcBenchmarkRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BenchmarkService(
    private val benchmarkRepository: BenchmarkRepository,
    private val jdbcBenchmarkRepository: JdbcBenchmarkRepository,
    private val stringRedisTemplate: StringRedisTemplate
) {

    @Suppress("UNCHECKED_CAST")
    private val keySerializer = stringRedisTemplate.keySerializer as RedisSerializer<String>
    
    @Suppress("UNCHECKED_CAST")
    private val valueSerializer = stringRedisTemplate.valueSerializer as RedisSerializer<String>

    fun runBenchmark(request: BenchmarkRequest): BenchmarkResponse {
        val count = request.count.coerceIn(1, 10000)
        val payloadSize = request.payloadSize.coerceIn(1, 10240)
        val results = mutableListOf<BenchmarkMetric>()

        // Generate static data for this run to keep comparisons equal
        val data = generateTestData(count, payloadSize)

        for (target in request.targets) {
            for (operation in request.operations) {
                try {
                    val metric = executeTest(target, operation, data)
                    results.add(metric)
                } catch (e: Exception) {
                    // Log error and return failure metric
                    results.add(
                        BenchmarkMetric(
                            target = target,
                            operation = operation,
                            totalTimeMs = 0.0,
                            opsPerSec = 0.0,
                            avgTimeMs = 0.0,
                            minTimeMs = 0.0,
                            maxTimeMs = 0.0
                        )
                    )
                }
            }
        }

        return BenchmarkResponse(
            count = count,
            payloadSize = payloadSize,
            results = results
        )
    }

    private fun generateTestData(count: Int, payloadSize: Int): List<Pair<String, String>> {
        val payload = "A".repeat(payloadSize)
        val runId = UUID.randomUUID().toString().substring(0, 8)
        return (1..count).map { i ->
            Pair("bench:$runId:$i", payload)
        }
    }

    private fun executeTest(target: String, operation: String, data: List<Pair<String, String>>): BenchmarkMetric {
        val count = data.size

        // Setup & Prepare data if it's a read test
        val isRead = operation.startsWith("READ")
        if (isRead) {
            prepData(target, data)
        } else {
            cleanupTarget(target, data) // Make sure target is clean for write tests
        }

        var totalTimeMs: Double
        var minTimeMs: Double
        var maxTimeMs: Double
        var avgTimeMs: Double

        try {
            when (target) {
                "MYSQL_JPA" -> {
                    when (operation) {
                        "WRITE_SINGLE" -> {
                            val times = LongArray(count)
                            val startTotal = System.nanoTime()
                            for (i in 0 until count) {
                                val (key, value) = data[i]
                                val startOp = System.nanoTime()
                                benchmarkRepository.save(BenchmarkData(key, value))
                                val endOp = System.nanoTime()
                                times[i] = endOp - startOp
                            }
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            minTimeMs = times.minOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            maxTimeMs = times.maxOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            avgTimeMs = times.average() / 1_000_000.0
                        }
                        "WRITE_BATCH" -> {
                            val entities = data.map { BenchmarkData(it.first, it.second) }
                            val startTotal = System.nanoTime()
                            benchmarkRepository.saveAll(entities)
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            avgTimeMs = totalTimeMs / count
                            minTimeMs = avgTimeMs
                            maxTimeMs = avgTimeMs
                        }
                        "READ_SINGLE" -> {
                            val times = LongArray(count)
                            val startTotal = System.nanoTime()
                            for (i in 0 until count) {
                                val (key, _) = data[i]
                                val startOp = System.nanoTime()
                                benchmarkRepository.findById(key)
                                val endOp = System.nanoTime()
                                times[i] = endOp - startOp
                            }
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            minTimeMs = times.minOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            maxTimeMs = times.maxOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            avgTimeMs = times.average() / 1_000_000.0
                        }
                        "READ_BATCH" -> {
                            val keys = data.map { it.first }
                            val startTotal = System.nanoTime()
                            benchmarkRepository.findAllById(keys)
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            avgTimeMs = totalTimeMs / count
                            minTimeMs = avgTimeMs
                            maxTimeMs = avgTimeMs
                        }
                        else -> throw IllegalArgumentException("Unknown operation: $operation")
                    }
                }
                "MYSQL_JDBC" -> {
                    when (operation) {
                        "WRITE_SINGLE" -> {
                            val times = LongArray(count)
                            val startTotal = System.nanoTime()
                            for (i in 0 until count) {
                                val (key, value) = data[i]
                                val startOp = System.nanoTime()
                                jdbcBenchmarkRepository.saveSingle(key, value)
                                val endOp = System.nanoTime()
                                times[i] = endOp - startOp
                            }
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            minTimeMs = times.minOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            maxTimeMs = times.maxOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            avgTimeMs = times.average() / 1_000_000.0
                        }
                        "WRITE_BATCH" -> {
                            val startTotal = System.nanoTime()
                            jdbcBenchmarkRepository.saveBatch(data)
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            avgTimeMs = totalTimeMs / count
                            minTimeMs = avgTimeMs
                            maxTimeMs = avgTimeMs
                        }
                        "READ_SINGLE" -> {
                            val times = LongArray(count)
                            val startTotal = System.nanoTime()
                            for (i in 0 until count) {
                                val (key, _) = data[i]
                                val startOp = System.nanoTime()
                                jdbcBenchmarkRepository.findById(key)
                                val endOp = System.nanoTime()
                                times[i] = endOp - startOp
                            }
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            minTimeMs = times.minOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            maxTimeMs = times.maxOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            avgTimeMs = times.average() / 1_000_000.0
                        }
                        "READ_BATCH" -> {
                            val keys = data.map { it.first }
                            val startTotal = System.nanoTime()
                            jdbcBenchmarkRepository.findAllByIds(keys)
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            avgTimeMs = totalTimeMs / count
                            minTimeMs = avgTimeMs
                            maxTimeMs = avgTimeMs
                        }
                        else -> throw IllegalArgumentException("Unknown operation: $operation")
                    }
                }
                "REDIS" -> {
                    when (operation) {
                        "WRITE_SINGLE" -> {
                            val times = LongArray(count)
                            val startTotal = System.nanoTime()
                            for (i in 0 until count) {
                                val (key, value) = data[i]
                                val startOp = System.nanoTime()
                                stringRedisTemplate.opsForValue().set(key, value)
                                val endOp = System.nanoTime()
                                times[i] = endOp - startOp
                            }
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            minTimeMs = times.minOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            maxTimeMs = times.maxOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            avgTimeMs = times.average() / 1_000_000.0
                        }
                        "WRITE_BATCH" -> {
                            val map = data.toMap()
                            val startTotal = System.nanoTime()
                            stringRedisTemplate.opsForValue().multiSet(map)
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            avgTimeMs = totalTimeMs / count
                            minTimeMs = avgTimeMs
                            maxTimeMs = avgTimeMs
                        }
                        "READ_SINGLE" -> {
                            val times = LongArray(count)
                            val startTotal = System.nanoTime()
                            for (i in 0 until count) {
                                val (key, _) = data[i]
                                val startOp = System.nanoTime()
                                stringRedisTemplate.opsForValue().get(key)
                                val endOp = System.nanoTime()
                                times[i] = endOp - startOp
                            }
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            minTimeMs = times.minOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            maxTimeMs = times.maxOrNull()?.let { it / 1_000_000.0 } ?: 0.0
                            avgTimeMs = times.average() / 1_000_000.0
                        }
                        "READ_BATCH" -> {
                            val keys = data.map { it.first }
                            val startTotal = System.nanoTime()
                            stringRedisTemplate.opsForValue().multiGet(keys)
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            avgTimeMs = totalTimeMs / count
                            minTimeMs = avgTimeMs
                            maxTimeMs = avgTimeMs
                        }
                        else -> throw IllegalArgumentException("Unknown operation: $operation")
                    }
                }
                "REDIS_PIPELINE" -> {
                    when (operation) {
                        "WRITE_SINGLE", "WRITE_BATCH" -> {
                            val startTotal = System.nanoTime()
                            stringRedisTemplate.executePipelined { connection ->
                                data.forEach { (key, value) ->
                                    val rawKey = keySerializer.serialize(key)!!
                                    val rawValue = valueSerializer.serialize(value)!!
                                    connection.stringCommands().set(rawKey, rawValue)
                                }
                                null
                            }
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            avgTimeMs = totalTimeMs / count
                            minTimeMs = avgTimeMs
                            maxTimeMs = avgTimeMs
                        }
                        "READ_SINGLE", "READ_BATCH" -> {
                            val startTotal = System.nanoTime()
                            stringRedisTemplate.executePipelined { connection ->
                                data.forEach { (key, _) ->
                                    val rawKey = keySerializer.serialize(key)!!
                                    connection.stringCommands().get(rawKey)
                                }
                                null
                            }
                            val endTotal = System.nanoTime()
                            totalTimeMs = (endTotal - startTotal) / 1_000_000.0
                            avgTimeMs = totalTimeMs / count
                            minTimeMs = avgTimeMs
                            maxTimeMs = avgTimeMs
                        }
                        else -> throw IllegalArgumentException("Unknown operation: $operation")
                    }
                }
                else -> throw IllegalArgumentException("Unknown target: $target")
            }
        } finally {
            // Always clean up after running tests
            cleanupTarget(target, data)
        }

        val opsPerSec = if (totalTimeMs > 0) (count.toDouble() / totalTimeMs) * 1000.0 else 0.0

        return BenchmarkMetric(
            target = target,
            operation = operation,
            totalTimeMs = totalTimeMs,
            opsPerSec = opsPerSec,
            avgTimeMs = avgTimeMs,
            minTimeMs = minTimeMs,
            maxTimeMs = maxTimeMs
        )
    }

    private fun prepData(target: String, data: List<Pair<String, String>>) {
        cleanupTarget(target, data)
        when (target) {
            "MYSQL_JPA", "MYSQL_JDBC" -> {
                jdbcBenchmarkRepository.saveBatch(data)
            }
            "REDIS", "REDIS_PIPELINE" -> {
                stringRedisTemplate.executePipelined { connection ->
                    data.forEach { (key, value) ->
                        val rawKey = keySerializer.serialize(key)!!
                        val rawValue = valueSerializer.serialize(value)!!
                        connection.stringCommands().set(rawKey, rawValue)
                    }
                    null
                }
            }
        }
    }

    private fun cleanupTarget(target: String, data: List<Pair<String, String>>) {
        when (target) {
            "MYSQL_JPA", "MYSQL_JDBC" -> {
                jdbcBenchmarkRepository.deleteAll()
            }
            "REDIS", "REDIS_PIPELINE" -> {
                val keys = data.map { it.first }
                stringRedisTemplate.delete(keys)
            }
        }
    }
}
