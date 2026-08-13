package com.theskysid.echobackend.messaging.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Transient presence-event payload broadcast to /topic/public (JOIN/LEAVE).
 * Not persisted — the old private-message table is gone.
 */
@Data
public class ChatMessage {

    private String sender;
    private String content;
    private String color;
    private LocalDateTime timestamp;
    private MessageType type;

    public enum MessageType {
        JOIN, LEAVE
    }
}
