package com.theskysid.echobackend.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Two Groq-backed models, split by how hard the call is rather than by feature.
 *
 * The reasoning model answers RAG questions and classifies conflicts — the
 * conflict classifier has to hold two statements side by side, decide whether
 * they concern the same underlying decision, and only then decide whether one
 * settles the other. A small model surface-matches keywords there ("switch",
 * "decided") and CONFLICT_SYSTEM grew a wall of counter-examples to fight it.
 * It runs only on text that already passed the extractor, so it is rare enough
 * for the bigger model to cost almost nothing.
 *
 * Groq decommissioned every Llama chat model, so the ids here are the gpt-oss
 * pair. Both reply with a bare token and no reasoning preamble, which the
 * exact-match parsers in DecisionService require. Check the id against
 * https://console.groq.com/docs/models before changing it — a dead id fails
 * every call, and the pipeline degrades silently to "nobody decided anything".
 *
 * The fast model runs the decision extractor — one short input, a YES/NO answer,
 * on every single message — and title generation, which is cosmetic.
 *
 * Both are pinned to temperature 0. Two of the three calls are single-token
 * classifications whose whole contract is an exact-match reply; langchain4j
 * otherwise defaults to 0.7, which made the same message classify differently
 * between runs and made any measurement of the prompts meaningless.
 */
@Configuration
public class LlmConfig {

    @Value("${spring.ai.groq.api-key:}")
    private String apiKey;

    @Value("${spring.ai.groq.model:openai/gpt-oss-120b}")
    private String modelName;

    @Value("${spring.ai.groq.fast-model:openai/gpt-oss-20b}")
    private String fastModelName;

    /** RAG answers and conflict classification. Injected wherever no qualifier is given. */
    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        return groqModel(modelName);
    }

    /** Decision extraction and titles — the per-message hot path. */
    @Bean
    public ChatLanguageModel fastChatLanguageModel() {
        return groqModel(fastModelName);
    }

    private ChatLanguageModel groqModel(String model) {
        return OpenAiChatModel.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
