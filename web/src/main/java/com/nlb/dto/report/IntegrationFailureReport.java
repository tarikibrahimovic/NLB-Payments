package com.nlb.dto.report;

import com.nlb.domain.IntegrationFailure;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class IntegrationFailureReport {
    private UUID failureId;
    private String context;
    private String entityName;
    private UUID relatedId;
    private String message;
    private String payload;
    private int retryCount;
    private Instant occurredAt;

    public static IntegrationFailureReport fromEntity(IntegrationFailure failure) {
        return IntegrationFailureReport.builder()
                .failureId(failure.getId())
                .context(failure.getContext())
                .entityName(failure.getEntityName())
                .relatedId(failure.getRelatedId())
                .message(failure.getMessage())
                .payload(failure.getPayload())
                .retryCount(failure.getRetryCount())
                .occurredAt(failure.getOccurredAt())
                .build();
    }
}
