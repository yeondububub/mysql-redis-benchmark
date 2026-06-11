package com.example.mysqlredisbenchmark.dto

data class RankingInitRequest(
    val count: Int
)

data class RankingRunRequest(
    val offset: Int,
    val limit: Int
)

data class RankingCurveRequest(
    val limit: Int
)

data class RankingRecord(
    val rank: Int,
    val userId: String,
    val score: Int
)

data class RankingBenchmarkResult(
    val offset: Int,
    val limit: Int,
    val mysqlTimeMs: Double,
    val redisTimeMs: Double,
    val mysqlResults: List<RankingRecord>,
    val redisResults: List<RankingRecord>
)

data class CurvePoint(
    val offset: Int,
    val mysqlTimeMs: Double,
    val redisTimeMs: Double
)

data class RankingCurveResult(
    val limit: Int,
    val points: List<CurvePoint>
)
