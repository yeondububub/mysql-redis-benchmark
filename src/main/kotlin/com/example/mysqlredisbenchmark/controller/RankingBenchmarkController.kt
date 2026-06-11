package com.example.mysqlredisbenchmark.controller

import com.example.mysqlredisbenchmark.dto.RankingBenchmarkResult
import com.example.mysqlredisbenchmark.dto.RankingCurveResult
import com.example.mysqlredisbenchmark.dto.RankingCurveRequest
import com.example.mysqlredisbenchmark.dto.RankingInitRequest
import com.example.mysqlredisbenchmark.dto.RankingRunRequest
import com.example.mysqlredisbenchmark.service.RankingBenchmarkService
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ranking")
@CrossOrigin(origins = ["*"])
class RankingBenchmarkController(private val rankingBenchmarkService: RankingBenchmarkService) {

    @PostMapping("/init")
    fun initializeData(@RequestBody request: RankingInitRequest): Map<String, String> {
        val message = rankingBenchmarkService.initializeRankingData(request.count)
        return mapOf("message" to message)
    }

    @PostMapping("/run")
    fun runBenchmark(@RequestBody request: RankingRunRequest): RankingBenchmarkResult {
        return rankingBenchmarkService.runRankingBenchmark(request.offset, request.limit)
    }

    @PostMapping("/curve")
    fun runCurveBenchmark(@RequestBody request: RankingCurveRequest): RankingCurveResult {
        return rankingBenchmarkService.runCurveBenchmark(request.limit)
    }
}
