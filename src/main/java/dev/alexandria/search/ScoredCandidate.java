package dev.alexandria.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.jspecify.annotations.Nullable;

/**
 * A search candidate with its raw score from a single source (vector or FTS). Used as input to
 * {@link ConvexCombinationFusion} for score normalisation and combination.
 *
 * @param embeddingId unique identifier for deduplication across vector and FTS results
 * @param segment the LangChain4j TextSegment (text + metadata)
 * @param embedding the embedding vector (from vector search; null for FTS-only hits)
 * @param score the raw score from the source (cosine similarity for vector, ts_rank for FTS)
 */
record ScoredCandidate(
    String embeddingId, TextSegment segment, @Nullable Embedding embedding, double score) {}
