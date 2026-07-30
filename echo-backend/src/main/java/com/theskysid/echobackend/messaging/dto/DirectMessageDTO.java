package com.theskysid.echobackend.messaging.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DirectMessageDTO {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderUsername;
    private String recipientUsername;
    private String content;
    private LocalDateTime timestamp;
    private LocalDateTime expiresAt;
}
