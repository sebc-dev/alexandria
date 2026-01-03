# BGE reranker-v2-m3 for RAG on RunPod with Infinity

**Yes, `BAAI/bge-reranker-v2-m3` exists and is production-ready for your Alexandria project.** This 568M-parameter multilingual reranker supports **8,192-token context** and requires only **~1.5GB VRAM** in FP16—well within L4 GPU capacity. Infinity inference server natively supports all BGE rerankers with a straightforward `/rerank` API endpoint. For technical documentation RAG, the optimal configuration is retrieving **30-50 candidates** then reranking to **3-5 results**, typically improving MRR by **30-40%** over semantic search alone with **200-400ms added latency**.

## Model verification and available variants

The exact HuggingFace path is `BAAI/bge-reranker-v2-m3`, released March 18, 2024, with over **2.8M monthly downloads**. This model sits in the middle of BAAI's reranker family—balancing multilingual capability with efficiency.

The complete BGE reranker lineup includes six models across three generations. The original v1 rerankers (September 2023) include **bge-reranker-base** at 278M parameters and **bge-reranker-large** at 560M parameters, both limited to 512-token context and Chinese/English only. The v2 generation (March 2024) introduced three models: **bge-reranker-v2-m3** with 568M parameters and 8K context, **bge-reranker-v2-gemma** at ~2.5B parameters for maximum accuracy, and **bge-reranker-v2-minicpm-layerwise** at ~2.7B with configurable layer selection for speed/accuracy tradeoffs. The newest **bge-reranker-v2.5-gemma2-lightweight** (July 2024) scales to 9B parameters with token compression support.

For Alexandria's single-user technical documentation use case, **bge-reranker-v2-m3 is the optimal choice**—its 8K context handles long documentation passages, multilingual support covers international codebases, and the 568M footprint leaves headroom for concurrent embedding operations on L4.

## Technical specifications for deployment

The model architecture is a cross-encoder fine-tuned from BAAI's bge-m3 embedding backbone. Unlike bi-encoders that pre-compute document embeddings independently, cross-encoders process query-document pairs jointly at inference time—enabling nuanced relevance scoring at the cost of requiring GPU computation per query.

**Context length** maxes at 8,192 tokens for query plus document combined, though the default is 512 tokens (configurable via `max_length` parameter). For technical documentation with code blocks, you'll likely want chunks of 400-600 tokens to stay safely within limits while preserving context.

**Memory footprint** varies by precision: FP32 requires ~2.2GB VRAM, while **FP16 drops to ~1.5GB**—the recommended production configuration. The L4's 24GB VRAM easily accommodates bge-reranker-v2-m3 alongside an embedding model like bge-small-en-v1.5 (400MB) with substantial headroom.

**Inference latency** for reranking scales roughly linearly with document count. On modern GPUs comparable to L4, expect **150-400ms for 20 documents** and **300-800ms for 50 documents** in batched FP16 mode. Individual query-document pair latency runs ~15-25ms. Setting batch_size to 64 optimizes throughput on L4.

The input/output format follows standard cross-encoder conventions: input pairs as `[["query", "document1"], ["query", "document2"], ...]` with raw logit scores output. Apply sigmoid normalization (`normalize=True`) to map scores to 0-1 range for ranking.

## Performance benchmarks and comparisons

BGE reranker-v2-m3 delivers strong performance across multilingual benchmarks while remaining computationally accessible. On **BEIR** (English retrieval benchmark), it achieves **8-11% nDCG improvement** over unranked baseline when reranking top-100 candidates. **MIRACL** multilingual benchmarks show excellent cross-lingual transfer across 18 languages including Chinese, Japanese, Arabic, and European languages.

Compared to **Cohere Rerank v3**, BGE reranker-v2-m3 performs comparably in most scenarios—LlamaIndex benchmarks show hit rates within 2-3% of Cohere. The tradeoff: Cohere offers cloud convenience and enterprise SLAs while BGE provides self-hosting, zero API costs, and fine-tuning capability. For Alexandria's single-user architecture, self-hosted BGE eliminates ongoing API expenses and latency from external calls.

Against **ms-marco-MiniLM-L-6-v2** (the lightweight 22M-parameter alternative), bge-reranker-v2-m3 offers substantially higher accuracy at ~25x the parameters. MiniLM runs faster (~12ms per pair vs ~20ms) with minimal VRAM (~100MB), but lacks multilingual support and handles only 512-token context. For technical documentation quality, the bge-reranker-v2-m3 accuracy gains justify the modest resource increase.

The **NVIDIA NV-RerankQA-Mistral-4B-v3** outperforms bge-reranker-v2-m3 by ~14% but requires 7x the parameters and proportionally more VRAM—overkill for single-user documentation search.

## Infinity server deployment configuration

Infinity (michaelfeil/infinity) **natively supports all BGE rerankers** with production-tested compatibility. The maintainer has explicitly documented working examples with bge-reranker-base, bge-reranker-large, and bge-reranker-v2-m3.

**Docker deployment for L4 GPU:**
```bash
docker run -it --gpus all \
  -v $PWD/data:/app/.cache \
  -p 7997:7997 \
  michaelf34/infinity:latest \
  v2 \
  --model-id BAAI/bge-reranker-v2-m3 \
  --engine torch \
  --device cuda \
  --batch-size 64 \
  --dtype float16 \
  --port 7997
```

The **reranking API endpoint** follows OpenAI-compatible format at `POST /rerank`:
```json
{
  "model": "BAAI/bge-reranker-v2-m3",
  "query": "How to configure PostgreSQL connection pooling",
  "documents": ["doc1 text...", "doc2 text...", "doc3 text..."],
  "top_n": 5
}
```

Response returns relevance scores sorted descending with original indices for result ordering. For multi-model deployment (embedding + reranking), specify multiple `--model-id` flags with corresponding `--batch-size` values.

**RunPod-specific deployment** offers two paths. The **official worker-infinity-embedding** serverless endpoint handles scaling automatically—set `MODEL_NAMES=BAAI/bge-reranker-v2-m3`, `BACKEND=torch`, and `DTYPES=float16`. Alternatively, deploy a **custom pod** with the Docker command above for dedicated persistent capacity with predictable latency.

Key configuration parameters: use `--engine torch` (not optimum) for rerankers, enable `--bettertransformer` for FlashAttention optimizations, and set `--dtype float16` for memory efficiency without measurable accuracy loss.

## Two-stage retrieval architecture for Alexandria

The optimal pattern combines fast vector search with precise cross-encoder reranking. Stage one uses pgvector semantic search to retrieve initial candidates quickly—ANN queries return in sub-100ms even at million-document scale. Stage two passes candidates through bge-reranker-v2-m3 for joint query-document relevance scoring, reordering results by true semantic alignment.

For technical documentation, research consistently recommends **N=30-50 initial candidates** narrowed to **K=3-5 final results**. Pinecone's standard configuration uses N=25, K=3; Databricks/ZeroEntropy research finds N=50 captures the quality sweet spot for most applications. Going deeper (N=100+) yields diminishing returns—Elastic Labs' "90% rule" shows you achieve 90% of maximum effectiveness at ~100 documents, with 3x compute savings versus exhaustive depth.

The expected improvement is substantial. LlamaIndex benchmarks show **+33% MRR improvement** (0.53→0.71) when adding CohereRerank to OpenAI embeddings. Hit rates improve 7-8% consistently across embedding models. For technical documentation specifically, reranking surfaces **precise API references and code examples** over general explanations—critical for developer-facing search.

**When reranking adds value for Alexandria:**
- Complex multi-intent queries ("How to configure X and troubleshoot Y")
- Queries requiring disambiguation between similar technical concepts
- Hybrid retrieval results merging keyword and semantic matches
- Any query where initial retrieval shows relevant results outside top-3

**When to skip reranking:** Simple factual lookups with clear keyword matches, queries where pgvector consistently returns accurate top-3 results, and real-time use cases requiring sub-200ms total latency.

## Practical deployment recommendations

For Alexandria's architecture—PostgreSQL + pgvector with RunPod GPU offloading—implement reranking as an async API call in your retrieval pipeline. After pgvector returns 30-50 candidates, POST to Infinity's `/rerank` endpoint and await reordered results before LLM context assembly.

**Recommended configuration:**
- **Model**: `BAAI/bge-reranker-v2-m3` (best balance for multilingual technical docs)
- **Initial retrieval (N)**: 30-50 documents
- **Final results (K)**: 3-5 documents for LLM context
- **Chunk size**: 400-600 tokens (fits 8K context with query overhead)
- **Precision**: FP16 (1.5GB VRAM, negligible accuracy loss)
- **Batch size**: 64 (optimized for L4 throughput)
- **Expected latency**: 200-400ms for 30-50 documents

Consider running both embedding (bge-small-en-v1.5 or bge-m3) and reranking models on the same Infinity instance to amortize RunPod costs. The L4's 24GB VRAM handles both comfortably. For cost optimization in low-query-volume scenarios, RunPod's serverless endpoints with scale-to-zero eliminate idle GPU charges—though cold starts add 10-30 seconds for model loading.