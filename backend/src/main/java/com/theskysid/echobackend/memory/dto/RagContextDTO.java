package com.theskysid.echobackend.memory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RagContextDTO {
    private String answer;
    private List<Long> sourceIds;

    // Retrieval arm that produced this answer — see recall.retrieval.mode.
    private String mode;

    // Raw text of every chunk handed to the LLM, in prompt order. Lets an
    // evaluation tell a retrieval failure (stale chunk was fetched) apart from
    // a synthesis failure (it was not, and the LLM still answered wrong).
    private List<String> retrieved;
}
