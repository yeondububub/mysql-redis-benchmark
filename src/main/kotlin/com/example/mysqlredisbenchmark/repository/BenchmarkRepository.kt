package com.example.mysqlredisbenchmark.repository

import com.example.mysqlredisbenchmark.model.BenchmarkData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BenchmarkRepository : JpaRepository<BenchmarkData, String>
