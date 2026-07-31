package com.theskysid.echobackend.channel.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChannelMessageDTO {
    private Long id;
    private Long channelId;
    private String sender;
    private String content;
    private String color;
    private String type;
    private LocalDateTime timestamp;
}
