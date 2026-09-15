package com.cocky.cockyserver.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record AnonymousToggleRequest(
        @NotNull(message = "anonymousDefault는 필수입니다.") Boolean anonymousDefault
) {
}
