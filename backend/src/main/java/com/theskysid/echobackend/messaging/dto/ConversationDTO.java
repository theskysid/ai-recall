package com.theskysid.echobackend.messaging.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationDTO {
    private Long id;
    private Long otherUserId;
    private String otherUsername;
    private String otherDisplayName;
    private boolean otherUserOnline;
    private String retentionPolicy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
