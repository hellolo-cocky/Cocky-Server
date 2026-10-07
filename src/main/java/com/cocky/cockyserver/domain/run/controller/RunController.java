package com.cocky.cockyserver.domain.run.controller;

import com.cocky.cockyserver.domain.run.dto.CodeRunRequest;
import com.cocky.cockyserver.domain.run.dto.CodeRunResponse;
import com.cocky.cockyserver.domain.run.service.RunService;
import com.cocky.cockyserver.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/run")
@RequiredArgsConstructor
public class RunController {

    private final RunService runService;

    @PostMapping
    public ResponseEntity<CodeRunResponse> run(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody CodeRunRequest request) {
        return ResponseEntity.ok(CodeRunResponse.from(runService.run(principal.userId(), request)));
    }
}
