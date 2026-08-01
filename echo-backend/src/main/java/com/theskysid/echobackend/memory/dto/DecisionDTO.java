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
    private String content;
    private String sourceType;
    private Long sourceId;
    private boolean superseded;
    private LocalDateTime createdAt;
}
