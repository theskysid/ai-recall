package com.theskysid.echobackend.config;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One call per model at startup, to prove the configured ids still exist.
 *
 * Groq decommissioned every Llama model while this app was pointing at
 * llama-3.1-8b-instant. Nothing failed loudly: extractDecision caught the
 * model_not_found, returned LLM_ERROR, and stored the message as an ordinary
 * memory — so the decision pipeline recorded nothing at all for roughly two
 * weeks and looked, from the outside, exactly like a quiet channel.
 *
 * A dead id is a deployment mistake, not a runtime condition, so it belongs at
 * startup where a deploy can catch it. Deliberately non-fatal: chat, calls and
 * transcription do not need Groq, and refusing to boot over an LLM id would
 * take the whole app down with one feature.
 */
@Component
public class LlmStartupCheck {

    private static final Logger logger = LoggerFactory.getLogger(LlmStartupCheck.class);

    /** Marker for grepping a dead model id out of the logs. */
    static final String DEAD_MODEL_MARKER = "GROQ_MODEL_UNUSABLE";

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    @Qualifier("fastChatLanguageModel")
    private ChatLanguageModel fastChatLanguageModel;

    @Value("${spring.ai.groq.model:}")
    private String modelName;

    @Value("${spring.ai.groq.fast-model:}")
    private String fastModelName;

    @PostConstruct
    void verifyModelsAreReachable() {
        check(modelName, chatLanguageModel);
        check(fastModelName, fastChatLanguageModel);
    }

    private void check(String name, ChatLanguageModel model) {
        try {
            model.generate(List.of(UserMessage.from("ping")));
            logger.info("Groq model {} is reachable", name);
        } catch (Exception e) {
            // Message only — an exception from the HTTP client can carry the
            // request headers, and those hold the API key.
            logger.error("{} '{}' failed: {}. Decision extraction and RAG answers "
                            + "will silently degrade until this id is corrected — see "
                            + "https://console.groq.com/docs/models",
                    DEAD_MODEL_MARKER, name, e.getMessage());
        }
    }
}
