package com.cocky.cockyserver.domain.user.dto;

import com.cocky.cockyserver.domain.user.entity.User;

/**
 * isAnonymousDefault는 Notion API 명세에 있는 필드명을 그대로 따른다 — record 컴포넌트명이
 * 곧 JSON 키가 되므로 "is" 접두어까지 포함해 명세와 일치시킨다.
 */
public record UserMeResponse(
        Long id, String email, String name, String department, Integer grade, Integer classNo, Integer number,
        String role, boolean isAnonymousDefault) {

    public static UserMeResponse from(User user) {
        return new UserMeResponse(user.getId(), user.getEmail(), user.getName(), user.getDepartment(),
                user.getGrade(), user.getClassNo(), user.getNumber(), user.getRole().name(),
                user.isAnonymousDefault());
    }
}
