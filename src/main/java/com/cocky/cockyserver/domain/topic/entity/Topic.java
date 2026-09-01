package com.cocky.cockyserver.domain.topic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    // DB 컬럼명은 week_order 그대로 둔다 — 컬럼 리네임은 별도 Flyway 마이그레이션이 필요해서
    // 데모 이후로 미룬다(라운드 주기 결정 A안, 2026-09 논의 참고).
    @Column(name = "week_order", nullable = false, columnDefinition = "TINYINT")
    private Integer topicOrder;

    public Topic(String name, Integer topicOrder) {
        this.name = name;
        this.topicOrder = topicOrder;
    }
}
