package com.cp.ecommerce.adapter.ai.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds the in-memory {@link VectorStore} that grounds the support assistant's policy answers (see ADR 0020), by embedding the
 * bundled knowledge-base markdown documents at startup.
 *
 * <p>
 * A {@link SimpleVectorStore} (in-process, nothing persisted) is a deliberate choice over a dedicated vector database like
 * pgvector: the knowledge base is a handful of small, bundled, read-only documents re-embedded fresh on every startup, not a
 * large or frequently-updated corpus, so the operational cost of a real vector database would not be justified for this
 * showcase feature - see ADR 0020 for the full trade-off discussion.
 * </p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "true")
public class SupportKnowledgeBaseConfiguration {

    static final String KNOWLEDGE_BASE_LOCATION_PATTERN = "classpath:support-knowledge-base/*.md";

    private static final String SOURCE_METADATA_KEY = "source";

    @Bean
    public VectorStore supportKnowledgeBaseVectorStore(final EmbeddingModel embeddingModel) {

        final VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        final TokenTextSplitter splitter = TokenTextSplitter.builder().build();
        final List<Resource> knowledgeBaseResources = loadKnowledgeBaseResources();
        knowledgeBaseResources.forEach(resource -> vectorStore.add(splitter.split(toDocument(resource))));
        log.info("Loaded {} support knowledge-base document(s) into the in-memory vector store", knowledgeBaseResources.size());
        return vectorStore;
    }

    private List<Resource> loadKnowledgeBaseResources() {

        try {
            return List.of(new PathMatchingResourcePatternResolver().getResources(KNOWLEDGE_BASE_LOCATION_PATTERN));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load support knowledge-base resources", exception);
        }
    }

    private Document toDocument(final Resource resource) {

        try {
            final String content = resource.getContentAsString(StandardCharsets.UTF_8);
            return new Document(content, Map.of(SOURCE_METADATA_KEY, String.valueOf(resource.getFilename())));
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Could not read support knowledge-base resource: " + resource.getFilename(),
                    exception);
        }
    }

}
