package com.theskysid.echobackend.messaging.dto;

import lombok.Data;

@Data
public class DirectMessageRequestDTO {
    private Long conversationId;
    private String senderUsername;
    private String recipientUsername;
    private String content;
    private String type; // "MESSAGE" or "TYPING"
}
