package com.theskysid.echobackend.memory.service;

import org.springframework.stereotype.Service;

@Service
public class DecisionService {

    /**
     * Whether the given text represents a "decision" worth tracking for
     * supersession. Stub for now — LLM extraction is implemented later.
     */
    public boolean isDecision(String text) {
        return false;
    }
}
