package com.example.mysqlredisbenchmark.controller

import com.example.mysqlredisbenchmark.dto.BenchmarkRequest
import com.example.mysqlredisbenchmark.dto.BenchmarkResponse
import com.example.mysqlredisbenchmark.service.BenchmarkService
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/benchmark")
@CrossOrigin(origins = ["*"]) // Enable CORS for development
class BenchmarkController(private val benchmarkService: BenchmarkService) {

    @PostMapping("/run")
    fun runBenchmark(@RequestBody request: BenchmarkRequest): BenchmarkResponse {
        return benchmarkService.runBenchmark(request)
    }
}
