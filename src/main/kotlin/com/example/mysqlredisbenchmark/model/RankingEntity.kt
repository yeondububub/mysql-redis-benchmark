package com.example.mysqlredisbenchmark.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "ranking",
    indexes = [Index(name = "idx_score", columnList = "score")]
)
class RankingEntity(
    @Id
    @Column(name = "user_id", length = 50)
    var userId: String = "",

    @Column(name = "score")
    var score: Int = 0
)
