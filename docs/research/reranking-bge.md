# Complete guide to bge-reranker-v2-m3 for two-stage RAG retrieval

Building Alexandria with **bge-reranker-v2-m3** is an excellent choice for your multilingual RAG pipeline. This 568M-parameter model offers the best quality-to-efficiency ratio among open-source rerankers, supporting 100+ languages including French and English with Apache 2.0 licensing. For your 1K-10K chunk corpus, the optimal configuration is retrieving **20-40 candidates** from pgvector and reranking to the **top 3-5 results**—a ratio delivering strong precision gains with latency under 500ms on modest GPU hardware.

## Technical specifications and input format

The model processes query-passage pairs using XLM-RoBERTa tokenization with a **512 combined token limit**. No special separator or template is required—the tokenizer handles pair encoding automatically.

```python
from FlagEmbedding import FlagReranker

# Initialize with FP16 for ~1GB VRAM usage
reranker = FlagReranker('BAAI/bge-reranker-v2-m3', use_fp16=True, devices=["cuda:0"])

# Score multiple pairs
pairs = [['what is panda?', 'hi'], ['what is panda?', 'The giant panda is endemic to China.']]
scores = reranker.compute_score(pairs)
# Returns: [-8.1875, 5.26171875] (raw logits)

# Normalized to 0-1 range
normalized = reranker.compute_score(pairs, normalize=True)
# Returns: [0.0003, 0.9948]
```

The model outputs **unbounded raw logits** where higher values indicate greater relevance. Negative scores like **-8.0** represent poor matches while positive scores like **5.0+** indicate strong relevance. Apply sigmoid normalization for interpretable 0-1 scores suitable for threshold filtering.

| Specification | Value |
|--------------|-------|
| Parameters | 568 million |
| Weights size | 2.29 GB (disk) |
| VRAM (FP16) | ~1.06 GB |
| VRAM (FP32) | ~2.27 GB |
| Max tokens | 512 combined |
| Base architecture | XLM-RoBERTa (bge-m3) |
| License | Apache 2.0 |

## Multilingual performance for French and English

The model excels at multilingual retrieval with **French and English performing nearly identically** at ~60-61 nDCG@10 on MIRACL benchmarks. Cross-lingual queries work effectively—an English query retrieves French documents with only minor degradation versus monolingual retrieval.

On MKQA benchmarks, French cross-lingual retrieval (French queries → English passages) achieves **76.2 Recall@100**, confirming robust cross-language capability. The unified multilingual semantic space learned during training handles code-switching and mixed-language corpora without configuration changes.

| Language | MIRACL nDCG@10 | Notes |
|----------|----------------|-------|
| Average (18 languages) | 69.32 | Reranking bge-m3 top-100 |
| English | ~59-60 | Baseline performance |
| French | ~60-61 | Equivalent to English |
| Japanese | ~75 | Above average |
| Finnish | ~80 | Strong performance |

For Alexandria's French/English technical documentation, no special handling is needed. The model naturally handles both languages and cross-lingual queries where developers might ask questions in English about French documentation or vice versa.

## How bge-reranker-v2-m3 compares to alternatives

Among open-source rerankers, **bge-reranker-v2-m3 offers the best balance** of quality, efficiency, and licensing for self-hosted deployments.

| Reranker | BEIR Avg | Parameters | Latency | License |
|----------|----------|------------|---------|---------|
| ms-marco-MiniLM-L-6-v2 | 0.577 | 33M | ~50ms | Apache 2.0 |
| jina-reranker-v2-base | 0.648 | 278M | ~150ms | CC-BY-NC |
| **bge-reranker-v2-m3** | **0.659** | 568M | ~200ms | **Apache 2.0** |
| bge-reranker-v2-gemma | Higher | 2B | ~400ms | Apache 2.0 |
| NV-RerankQA-Mistral-4B | 0.753 | 4B | ~500ms | NVIDIA License |
| Cohere Rerank 3.5 | ~0.70 | Unknown | ~400ms | Proprietary |

**Against BGE family members**: The v2-m3 variant provides **10-15% quality improvement** over bge-reranker-base/large while adding true multilingual support (100+ languages versus Chinese/English only). The larger bge-reranker-v2-gemma (2B parameters) offers better quality but requires **4x more VRAM** (~4-5GB).

**Against Cohere and Jina**: Commercial APIs like Cohere Rerank achieve slightly higher quality scores but introduce per-query costs (~$1/1000 queries) and vendor dependency. Jina's v2 reranker uses CC-BY-NC licensing, making bge-reranker-v2-m3 the clear choice for commercial applications requiring full ownership.

## Optimal N and K parameters for your corpus size

For Alexandria's **1K-10K chunk corpus**, the research consensus recommends retrieving **20-40 candidates** and reranking to **3-5 final results**.

| Corpus Size | Initial Retrieval (N) | Final Results (K) | Ratio |
|-------------|----------------------|-------------------|-------|
| 1K chunks | 15-20 | 3-4 | ~5:1 |
| 5K chunks | 25-30 | 3-5 | ~6:1 |
| 10K chunks | 35-40 | 4-5 | ~8:1 |

**Why these numbers work**: Research from Elastic Search Labs demonstrates the **"90% rule"**—reranking approximately 100 candidates captures 90% of maximum effectiveness gains. For smaller corpora like yours, diminishing returns set in earlier. Pinecone's production recommendation of **top_k=25, top_n=3** (8:1 ratio) aligns with industry consensus.

The impact on recall follows a predictable curve:

| N Value | Typical Recall@N | Rerank Latency (A10) |
|---------|------------------|---------------------|
| 15 | 70-80% | ~100-150ms |
| 25 | 80-88% | ~150-250ms |
| 40 | 88-94% | ~250-400ms |
| 100 | 95-98% | ~500-800ms |

**For LLM context windows**: With Claude's large context, you can safely return K=5-8 chunks of ~500-800 tokens each. The "Lost in the Middle" research finding indicates that **LLM recall degrades as context fills**—fewer, highly-relevant chunks outperform many marginally-relevant ones.

## When reranking adds value and when to skip it

Implement **adaptive reranking** to balance quality and latency. Skip the reranker when the initial retrieval already provides high-confidence results.

```python
def smart_retrieve(query: str, corpus_size: int):
    # Determine N based on corpus size
    n = 15 if corpus_size <= 1000 else (25 if corpus_size <= 5000 else 40)
    
    # Initial vector search
    candidates = pgvector_search(query, top_k=n)
    
    # Decision logic for skipping rerank
    top_score = candidates[0].similarity
    score_variance = calculate_variance([c.similarity for c in candidates[:5]])
    
    # Skip rerank conditions
    if top_score > 0.92 and score_variance < 0.05:
        return candidates[:4]  # High confidence, clear winner
    
    if len(query.split()) < 4 and top_score > 0.85:
        return candidates[:4]  # Simple query with good match
    
    # Otherwise, rerank
    return rerank(query, candidates)[:4]
```

**Reranking is most valuable when**:
- Initial retrieval scores cluster tightly (hard to distinguish top results)
- Complex multi-part queries requiring nuanced understanding
- Technical queries where keyword overlap doesn't guarantee relevance
- Cross-lingual scenarios where embedding similarity may be less reliable

**Skip reranking when**:
- Top embedding score exceeds **0.90** with clear separation from runner-up
- Simple factual queries under 5 words with strong initial match
- Small, well-curated corpora under 1,000 specialized documents
- Query patterns matching document titles or headers exactly

Industry data suggests adaptive reranking can **reduce reranker calls by 40-60%** while maintaining quality for the remaining queries.

## Performance, latency, and GPU requirements

Running bge-reranker-v2-m3 on RunPod with **FP16 inference** achieves practical latencies for interactive applications.

| Candidates | T4 (16GB) | A10 (24GB) | A100 (40GB) |
|------------|-----------|------------|-------------|
| N=25 | ~150-250ms | ~100-150ms | ~50-100ms |
| N=50 | ~300-500ms | ~200-300ms | ~100-150ms |
| N=100 | ~600-900ms | ~400-600ms | ~200-300ms |

**GPU recommendations for RunPod**:
- **T4**: Minimum viable option, ~3-5s for 100 documents (budget choice)
- **A10/L4**: Optimal balance at ~1-2s for 100 documents (recommended)
- **A100**: Premium option for lowest latency, batch processing

**Memory requirements**: The model fits comfortably on any modern GPU. With FP16, you need only **~1.06GB VRAM**, leaving ample headroom for batching and co-locating the embedding model.

**Batching strategy**: Always batch rerank calls. The model processes pairs efficiently in batches of 32-64, turning sequential 25ms-per-pair calls into bulk operations. On A10, throughput reaches **25-50 pairs/second** with proper batching.

## Score normalization and combining with embeddings

For two-stage retrieval, **use reranker scores exclusively** for final ordering. The cross-encoder directly models query-document relevance, making combination with embedding scores typically unnecessary.

```python
# Recommended: Reranker-only scoring
reranked_docs = sorted(
    zip(docs, reranker.compute_score(pairs, normalize=True)),
    key=lambda x: x[1],
    reverse=True
)
```

**When hybrid scoring is needed** (combining multiple retrieval sources), use **Reciprocal Rank Fusion (RRF)** with k=60:

```python
def rrf_score(ranks: list[int], k: int = 60) -> float:
    return sum(1.0 / (k + rank) for rank in ranks)

# Combine dense retrieval rank + BM25 rank + reranker rank
combined_score = rrf_score([dense_rank, bm25_rank, rerank_rank])
```

RRF avoids scale mismatch issues by focusing on **rank positions** rather than raw scores. The k=60 constant (industry standard) prevents the top-ranked document from having disproportionate influence over second place.

**Post-rerank filtering thresholds**: Apply a normalized score cutoff of **0.3-0.5** after sigmoid transformation. Use dynamic thresholds based on score distribution rather than fixed values:

```python
def filter_results(reranked_docs, min_ratio=0.5):
    max_score = reranked_docs[0].score
    threshold = max_score * min_ratio
    return [doc for doc in reranked_docs if doc.score >= threshold]
```

## Deployment architecture for RunPod

**Recommended setup**: Deploy embedding and reranking on the **same GPU endpoint** to eliminate inter-service network latency.

```python
# Combined handler for RunPod serverless
import runpod
from FlagEmbedding import FlagReranker, FlagModel

# Load both models at container start
EMBEDDER = FlagModel('Qwen/Qwen3-Embedding-0.6B', use_fp16=True)
RERANKER = FlagReranker('BAAI/bge-reranker-v2-m3', use_fp16=True)

def handler(event):
    input_data = event["input"]
    operation = input_data.get("operation")
    
    if operation == "embed":
        return {"embeddings": EMBEDDER.encode(input_data["texts"]).tolist()}
    
    elif operation == "rerank":
        pairs = [[input_data["query"], doc] for doc in input_data["documents"]]
        scores = RERANKER.compute_score(pairs, normalize=True)
        ranked = sorted(enumerate(scores), key=lambda x: x[1], reverse=True)
        return {"results": [{"index": i, "score": s} for i, s in ranked]}

runpod.serverless.start({"handler": handler})
```

**RunPod configuration**:
```yaml
gpu_type: "NVIDIA L4" or "A10G"
max_workers: 3
active_workers: 1  # Keep warm to avoid cold starts
flashboot: true    # Reduces cold start to ~200ms-2s
```

With Qwen3-Embedding-0.6B (~0.6GB) and bge-reranker-v2-m3 (~1GB), total VRAM usage stays under **2GB FP16**, fitting easily on any GPU with room for batching.

**Serverless vs. dedicated**: For Claude Code's bursty query patterns, **RunPod serverless with FlashBoot** provides cost-effective scaling. Set active_workers=1 to maintain a warm instance, eliminating cold starts for typical usage while scaling to zero during idle periods.

## Fallback strategy and error handling

Implement graceful degradation when the reranker is unavailable:

```typescript
async function rerankWithFallback(
  query: string,
  docs: Array<{text: string; similarity: number}>,
  topN: number = 5
): Promise<Array<{text: string; score: number}>> {
  try {
    const reranked = await rerankApi.rerank(query, docs, {
      timeout: 3000,
      retries: 2
    });
    return reranked.slice(0, topN);
  } catch (error) {
    console.warn('Reranker unavailable, using embedding scores');
    // Fall back to embedding similarity (already sorted)
    return docs.slice(0, topN).map(d => ({
      text: d.text,
      score: d.similarity
    }));
  }
}
```

**Caching strategy**: Cache reranking results by query-document-set hash with 1-hour TTL. For Claude Code usage patterns, repeated queries against the same documentation will benefit significantly from caching.

```python
import hashlib

def cache_key(query: str, doc_ids: list[str]) -> str:
    content = f"{query}:{':'.join(sorted(doc_ids))}"
    return hashlib.md5(content.encode()).hexdigest()
```

## Official resources and model identifiers

| Resource | Location |
|----------|----------|
| **Hugging Face Model** | `BAAI/bge-reranker-v2-m3` |
| **GitHub Repository** | github.com/FlagOpen/FlagEmbedding |
| **Documentation** | bge-model.com |
| **Technical Paper** | arXiv:2402.03216 (BGE-M3) |
| **Python Package** | `pip install FlagEmbedding` |

## Recommended configuration for Alexandria

Based on your specific requirements (Bun + TypeScript, PostgreSQL + pgvector 0.8.1, 1K-10K chunks, FR/EN documentation), here is the recommended configuration:

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Model | BAAI/bge-reranker-v2-m3 | Best open-source multilingual, Apache 2.0 |
| Initial retrieval (N) | 25-35 | Optimal recall for 5K-10K corpus |
| Final results (K) | 4-5 | Sufficient context without overwhelming LLM |
| Skip-rerank threshold | Cosine > 0.90 | High-confidence embedding results |
| Score threshold | Normalized > 0.35 | Filter low-relevance results |
| GPU | RunPod L4/A10 | ~200-300ms latency for N=30 |
| Deployment | Combined endpoint | Eliminates network hop between embed/rerank |
| Fallback | Embedding-only with warning | Graceful degradation |

This configuration delivers **sub-500ms total retrieval latency** (pgvector + reranking) while maintaining high precision for Claude Code's context retrieval needs.