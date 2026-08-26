package com.theskysid.echobackend.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Two beans now share the ChatLanguageModel type, so an unqualified injection
 * point is ambiguous unless @Primary resolves it. Spring only reports that at
 * context startup, and the one test that starts a context (contextLoads) needs
 * a database and mail host — so it cannot catch this on a developer machine.
 *
 * This starts a context holding nothing but LlmConfig: no DB, no network. A
 * dummy key is required because langchain4j's builder rejects a blank one at
 * construction; nothing here calls Groq.
 */
class LlmConfigTest {

    @Test
    void primaryResolvesTheReasoningModelAndTheQualifierResolvesTheFastOne() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test", java.util.Map.of("spring.ai.groq.api-key", "test-key")));
            ctx.register(PropertySourcesPlaceholderConfigurer.class, LlmConfig.class);
            ctx.refresh();

            // Unqualified injection (RagService, DecisionService.classifyConflict)
            // must not throw NoUniqueBeanDefinitionException, and must land on the
            // reasoning model rather than the cheap one.
            assertSame(ctx.getBean("chatLanguageModel"), ctx.getBean(ChatLanguageModel.class));
            assertNotSame(ctx.getBean("chatLanguageModel"), ctx.getBean("fastChatLanguageModel"));
        }
    }
}
