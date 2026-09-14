package com.cocky.cockyserver.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record AnonymousToggleRequest(
        @NotNull(message = "anonymous는 필수입니다.") Boolean anonymous
) {
}
