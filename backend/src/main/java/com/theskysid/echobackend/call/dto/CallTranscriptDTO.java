package com.theskysid.echobackend.call.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CallTranscriptDTO {
    private Long id;
    private Long channelId;
    private String audioUrl;
    private String fullTranscript;
    private LocalDateTime createdAt;
}
