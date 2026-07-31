package com.theskysid.echobackend.memory.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    // Free, fully local, in-process model — no external API calls. Outputs 384 dims.
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    /**
     * Embed a text string with the local all-MiniLM-L6-v2 model and return the
     * resulting float vector.
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Text to embed is required");
        }
        Embedding embedding = embeddingModel.embed(text).content();
        return embedding.vector();
    }
}
