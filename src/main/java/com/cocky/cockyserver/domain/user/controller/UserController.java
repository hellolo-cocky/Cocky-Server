package com.cocky.cockyserver.domain.user.controller;

import com.cocky.cockyserver.domain.user.dto.AnonymousToggleRequest;
import com.cocky.cockyserver.domain.user.dto.AnonymousToggleResponse;
import com.cocky.cockyserver.domain.user.dto.UserMeResponse;
import com.cocky.cockyserver.domain.user.service.UserService;
import com.cocky.cockyserver.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getMe(principal.userId()));
    }

    /** 본인만 호출 가능 — /me 경로라 principal의 userId 외 대상을 지정할 방법이 없다. */
    @PatchMapping("/me/anonymous")
    public ResponseEntity<AnonymousToggleResponse> toggleAnonymous(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody AnonymousToggleRequest request) {
        return ResponseEntity.ok(userService.setAnonymous(principal.userId(), request.anonymous()));
    }
}