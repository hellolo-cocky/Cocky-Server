package com.cocky.cockyserver.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "`user`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datagsm_id", nullable = false, unique = true)
    private Long datagsmId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer grade;

    @Column(name = "class_no", nullable = false, columnDefinition = "TINYINT")
    private Integer classNo;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer number;

    @Column(nullable = false, length = 20)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "refresh_token", length = 512)
    private String refreshToken;

    @Column(name = "anonymous_nickname", length = 50, unique = true)
    private String anonymousNickname;

    @Column(name = "is_anonymous_default", nullable = false)
    private boolean anonymousDefault;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public User(Long datagsmId, String email, String name, Integer grade, Integer classNo,
                 Integer number, String department, Role role) {
        this.datagsmId = datagsmId;
        this.email = email;
        this.name = name;
        this.grade = grade;
        this.classNo = classNo;
        this.number = number;
        this.department = department;
        this.role = role;
    }

    public void updateProfile(String name, Integer grade, Integer classNo, Integer number, String department) {
        this.name = name;
        this.grade = grade;
        this.classNo = classNo;
        this.number = number;
        this.department = department;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void clearRefreshToken() {
        this.refreshToken = null;
    }

    /** anonymousNickname이 이미 있으면 재생성하지 않고 그대로 둔다 — 호출부는 null일 때만 넘긴다. */
    public void updateAnonymousNickname(String anonymousNickname) {
        this.anonymousNickname = anonymousNickname;
    }

    public void updateAnonymousDefault(boolean anonymousDefault) {
        this.anonymousDefault = anonymousDefault;
    }
}
