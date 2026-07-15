package com.cocky.cockyserver.domain.user.dto;

import com.cocky.cockyserver.domain.user.entity.User;

public record UserMeResponse(
        Long id, String email, String name, String department, Integer grade, Integer classNo, Integer number,
        String role) {

    public static UserMeResponse from(User user) {
        return new UserMeResponse(user.getId(), user.getEmail(), user.getName(), user.getDepartment(),
                user.getGrade(), user.getClassNo(), user.getNumber(), user.getRole().name());
    }
}