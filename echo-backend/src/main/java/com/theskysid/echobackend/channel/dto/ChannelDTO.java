package com.theskysid.echobackend.channel.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChannelDTO {
    private Long id;
    private String name;
    private String description;
    private String inviteCode;
    private String ownerUsername;
    private boolean owner;
    private long memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime joinedAt;
}
