package com.theskysid.echobackend.memory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RagContextDTO {
    private String answer;
    private List<Long> sourceIds;

    // Raw text of every chunk handed to the LLM, in prompt order.
    private List<String> retrieved;
}
