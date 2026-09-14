package com.cocky.cockyserver.domain.user.repository;

import com.cocky.cockyserver.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByDatagsmId(Long datagsmId);

    /** 익명 닉네임 생성 시 중복 확인용(단계 3). */
    boolean existsByAnonymousNickname(String anonymousNickname);
}