package com.theskysid.echobackend.memory.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DecisionDTO {
    private UUID id;
    private Long channelId;
    private String title;
    private String content;
    private String sourceType;
    private Long sourceId;
    /** CURRENT | SUPERSEDED | UNRESOLVED. */
    private String status;
    /** Kept for existing callers; true exactly when status is SUPERSEDED. */
    private boolean superseded;
    private UUID conflictsWithId;
    private LocalDateTime createdAt;
}
