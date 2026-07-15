package com.cocky.cockyserver.domain.admin.dto;

import com.cocky.cockyserver.domain.admin.entity.AiGenerationLog;
import java.time.LocalDateTime;

public record AdminGenerationLogResponse(Long roundId, String reason, LocalDateTime failedAt) {

    public static AdminGenerationLogResponse from(AiGenerationLog log) {
        return new AdminGenerationLogResponse(log.getRound().getId(), log.getErrorMessage(), log.getCreatedAt());
    }
}
