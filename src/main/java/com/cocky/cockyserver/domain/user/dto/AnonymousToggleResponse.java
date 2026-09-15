package com.cocky.cockyserver.domain.user.dto;

import com.cocky.cockyserver.domain.user.entity.User;

public record AnonymousToggleResponse(boolean anonymousDefault, String anonymousNickname) {

    public static AnonymousToggleResponse from(User user) {
        return new AnonymousToggleResponse(user.isAnonymousDefault(), user.getAnonymousNickname());
    }
}
