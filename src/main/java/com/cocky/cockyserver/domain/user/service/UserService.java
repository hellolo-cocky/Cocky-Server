package com.cocky.cockyserver.domain.user.service;

import com.cocky.cockyserver.domain.user.dto.UserMeResponse;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.domain.user.exception.UserNotFoundException;
import com.cocky.cockyserver.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("존재하지 않는 사용자입니다. userId=" + userId));
        return UserMeResponse.from(user);
    }
}