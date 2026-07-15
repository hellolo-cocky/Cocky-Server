package com.cocky.cockyserver.domain.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.cocky.cockyserver.domain.admin.dto.AdminGenerationLogsResponse;
import com.cocky.cockyserver.domain.admin.entity.AiGenerationLog;
import com.cocky.cockyserver.domain.admin.entity.GenerationStatus;
import com.cocky.cockyserver.domain.admin.repository.AiGenerationLogRepository;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AiGenerationLogRepository aiGenerationLogRepository;

    private Round round() {
        return new Round(new Topic("배열", 1),
                LocalDate.of(2026, 7, 8), LocalDateTime.of(2026, 7, 8, 0, 0), LocalDateTime.of(2026, 7, 9, 0, 0));
    }

    @Test
    void returnsOnlyFailedLogsMappedToResponse() {
        AdminService service = new AdminService(aiGenerationLogRepository);
        AiGenerationLog failure = AiGenerationLog.failure(round(), 3, "AI 호출 타임아웃", 2);
        when(aiGenerationLogRepository.findTop50ByStatusOrderByCreatedAtDesc(GenerationStatus.FAILED))
                .thenReturn(List.of(failure));

        AdminGenerationLogsResponse response = service.getFailedGenerationLogs();

        assertEquals(1, response.logs().size());
        assertEquals("AI 호출 타임아웃", response.logs().get(0).reason());
        assertEquals(failure.getRound().getId(), response.logs().get(0).roundId());
        assertEquals(failure.getCreatedAt(), response.logs().get(0).failedAt());
    }

    @Test
    void returnsEmptyListWhenNoFailures() {
        AdminService service = new AdminService(aiGenerationLogRepository);
        when(aiGenerationLogRepository.findTop50ByStatusOrderByCreatedAtDesc(GenerationStatus.FAILED))
                .thenReturn(List.of());

        AdminGenerationLogsResponse response = service.getFailedGenerationLogs();

        assertEquals(0, response.logs().size());
    }
}
