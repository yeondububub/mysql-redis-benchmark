package com.example.mysqlredisbenchmark

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MysqlRedisBenchmarkApplication

fun main(args: Array<String>) {
    runApplication<MysqlRedisBenchmarkApplication>(*args)
}
