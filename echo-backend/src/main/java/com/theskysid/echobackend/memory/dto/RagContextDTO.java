package com.theskysid.echobackend.memory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RagContextDTO {
    private Long channelId;
    private String query;
    private String context;
    private List<Long> sourceIds;
}
