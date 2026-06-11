package com.example.mysqlredisbenchmark.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "benchmark_data")
class BenchmarkData(
    @Id
    @Column(name = "id")
    var id: String = "",

    @Column(name = "val_data", columnDefinition = "TEXT")
    var valData: String = ""
)
