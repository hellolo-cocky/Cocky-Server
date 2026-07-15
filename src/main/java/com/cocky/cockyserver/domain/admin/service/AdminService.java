package com.cocky.cockyserver.domain.admin.service;

import com.cocky.cockyserver.domain.admin.dto.AdminGenerationLogResponse;
import com.cocky.cockyserver.domain.admin.dto.AdminGenerationLogsResponse;
import com.cocky.cockyserver.domain.admin.entity.GenerationStatus;
import com.cocky.cockyserver.domain.admin.repository.AiGenerationLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AiGenerationLogRepository aiGenerationLogRepository;

    @Transactional(readOnly = true)
    public AdminGenerationLogsResponse getFailedGenerationLogs() {
        List<AdminGenerationLogResponse> logs = aiGenerationLogRepository
                .findTop50ByStatusOrderByCreatedAtDesc(GenerationStatus.FAILED).stream()
                .map(AdminGenerationLogResponse::from)
                .toList();
        return new AdminGenerationLogsResponse(logs);
    }
}
