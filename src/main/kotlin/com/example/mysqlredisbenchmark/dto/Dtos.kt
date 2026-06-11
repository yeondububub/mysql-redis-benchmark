package com.example.mysqlredisbenchmark.dto

data class BenchmarkRequest(
    val count: Int,
    val payloadSize: Int,
    val operations: List<String>, // WRITE_SINGLE, WRITE_BATCH, READ_SINGLE, READ_BATCH
    val targets: List<String> // MYSQL_JPA, MYSQL_JDBC, REDIS, REDIS_PIPELINE
)

data class BenchmarkMetric(
    val target: String,
    val operation: String,
    val totalTimeMs: Double,
    val opsPerSec: Double,
    val avgTimeMs: Double,
    val minTimeMs: Double,
    val maxTimeMs: Double
)

data class BenchmarkResponse(
    val count: Int,
    val payloadSize: Int,
    val results: List<BenchmarkMetric>
)
