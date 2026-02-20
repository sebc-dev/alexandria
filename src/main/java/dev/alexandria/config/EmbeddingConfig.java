package dev.alexandria.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore.SearchMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Configures the embedding model and vector store beans.
 *
 * <p>Uses the ONNX-based bge-small-en-v1.5 quantized model (384 dimensions) running
 * in-process, avoiding any external embedding API. The {@link PgVectorEmbeddingStore}
 * is configured in {@link SearchMode#HYBRID} mode with configurable RRF fusion constant
 * and shares the application's HikariCP {@link DataSource} to avoid duplicate connection pools.
 *
 * @see dev.alexandria.search.SearchService
 */
@Configuration
public class EmbeddingConfig {

    /**
     * Provides the in-process ONNX embedding model (bge-small-en-v1.5 quantized, 384 dimensions).
     *
     * @return a ready-to-use embedding model requiring no external API
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallEnV15QuantizedEmbeddingModel();
    }

    /**
     * Provides the in-process ONNX cross-encoder scoring model (ms-marco-MiniLM-L-6-v2)
     * for reranking search results.
     *
     * @param modelPath     path to the ONNX model file
     * @param tokenizerPath path to the tokenizer JSON file
     * @return a ready-to-use scoring model for cross-encoder reranking
     */
    @Bean
    public ScoringModel scoringModel(
            @Value("${alexandria.reranker.model-path}") String modelPath,
            @Value("${alexandria.reranker.tokenizer-path}") String tokenizerPath) {
        return new OnnxScoringModel(modelPath, tokenizerPath);
    }

    /**
     * Configures the pgvector embedding store in hybrid search mode.
     *
     * <p>Schema and HNSW index are managed by Flyway; {@code createTable} and {@code useIndex}
     * are disabled to avoid conflicts. The {@code textSearchConfig("english")} must match the
     * GIN index configuration in the V1 migration.
     *
     * @param dataSource the shared HikariCP data source (no duplicate pool)
     * @param rrfK       the RRF fusion constant (default 60, from original RRF paper)
     * @return a hybrid-search-capable embedding store backed by pgvector
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            DataSource dataSource,
            @Value("${alexandria.search.rrf-k:60}") int rrfK) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("document_chunks")
                .dimension(384)
                .createTable(false)  // Schema managed by Flyway migrations
                .useIndex(false)    // HNSW index managed by Flyway V1
                .searchMode(SearchMode.HYBRID)
                .textSearchConfig("english")  // Must match GIN index config in V1 migration
                .rrfK(rrfK)  // RRF constant (configurable via alexandria.search.rrf-k)
                .build();
    }
}
