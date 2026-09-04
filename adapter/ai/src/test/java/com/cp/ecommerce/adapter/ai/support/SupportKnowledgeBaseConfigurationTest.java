package com.cp.ecommerce.adapter.ai.support;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SupportKnowledgeBaseConfiguration}.
 */
@ExtendWith(MockitoExtension.class)
class SupportKnowledgeBaseConfigurationTest {

    // Every document (and every query) is stubbed to the same fixed embedding vector: cosine similarity between
    // identical vectors is 1.0, so any query trivially matches every loaded chunk - this test is only about our own
    // classpath-loading/splitting wiring, not Spring AI's own embedding-similarity maths.
    private static final float[] FIXED_EMBEDDING = { 1f, 0f, 0f };

    @Mock
    private transient EmbeddingModel embeddingModel;

    private final transient SupportKnowledgeBaseConfiguration configuration = new SupportKnowledgeBaseConfiguration();

    @Test
    void shouldLoadEveryBundledKnowledgeBaseDocumentIntoAQueryableVectorStore() {

        // ChatModel.getOptions()-style gotcha: EmbeddingModel.embed(..) default methods return null on a plain mock
        // unless the methods actually invoked internally are stubbed explicitly.
        when(embeddingModel.embed(any(Document.class))).thenReturn(FIXED_EMBEDDING);
        when(embeddingModel.embed(anyString())).thenReturn(FIXED_EMBEDDING);

        final VectorStore vectorStore = configuration.supportKnowledgeBaseVectorStore(embeddingModel);

        final List<Document> results = vectorStore.similaritySearch("Can I still cancel my order?");

        assertThat(results).isNotEmpty();
    }

}
