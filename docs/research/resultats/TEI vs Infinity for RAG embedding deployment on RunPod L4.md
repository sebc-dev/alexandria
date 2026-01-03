# TEI vs Infinity for RAG embedding deployment on RunPod L4

**Infinity emerges as the clear winner for this specific use case.** Its single-endpoint architecture serving both embedding and reranking models eliminates cold start duplication, reduces operational complexity, and costs approximately **40% less** than running two separate TEI endpoints on RunPod serverless. Both configurations fit comfortably within the L4's 24GB VRAM, but Infinity's official RunPod worker template and multi-model support provide a significant deployment advantage.

Critical finding: TEI now fully supports decoder-based models including **Qwen3-Embedding-0.6B** as of version 1.7+, resolving the primary compatibility concern. However, TEI cannot serve embedding and reranking models from a single instance, requiring two endpoints.

---

## Technical compatibility confirmed for both configurations

Both configurations are technically viable on L4 GPU (24GB VRAM), with ample headroom for batch processing.

**Configuration 1 (TEI with Qwen3-Embedding-0.6B + bge-reranker-v2-m3):**
TEI's GitHub README explicitly lists Qwen3 models among supported architectures, using "last-token" pooling for decoder models. The reranker is served via TEI's native `/rerank` endpoint. Combined VRAM usage sits around **3.5-4.5GB** at typical batch sizes, leaving ~20GB headroom.

**Configuration 2 (Infinity with bge-m3 + bge-reranker-v2-m3):**
Infinity's documentation lists both BAAI/bge-m3 and BAAI/bge-reranker-v2-m3 as explicitly tested models. Multi-model serving works via repeated `--model-id` flags, with both models sharing a single API endpoint. Combined VRAM usage runs **5-8GB**, leaving ~16-19GB headroom.

| Model | Base VRAM (FP16) | Typical Working Memory | Peak Usage |
|-------|------------------|----------------------|------------|
| Qwen3-Embedding-0.6B | ~1.2GB | 2-4GB | ~6GB |
| BAAI/bge-m3 | ~1.1GB | 2-4GB | ~10GB |
| bge-reranker-v2-m3 | ~0.6-1.2GB | 1.5-2GB | ~3GB |

---

## TEI architecture requires dual endpoints but delivers proven stability

TEI operates as a single-model-per-instance server, meaning your RAG pipeline requires two separate Docker containers and API endpoints.

**Embedding endpoint (Qwen3-Embedding-0.6B):**
```bash
docker run --gpus all -p 8080:80 -v $PWD/data:/data \
  ghcr.io/huggingface/text-embeddings-inference:1.8 \
  --model-id Qwen/Qwen3-Embedding-0.6B \
  --dtype float16 --max-batch-tokens 16384
```

**Reranker endpoint (bge-reranker-v2-m3):**
```bash
docker run --gpus all -p 8081:80 -v $PWD/data:/data \
  ghcr.io/huggingface/text-embeddings-inference:1.8 \
  --model-id BAAI/bge-reranker-v2-m3
```

TEI exposes an **OpenAI-compatible `/v1/embeddings` endpoint** for embeddings, enabling drop-in compatibility with most vector databases and Python clients. The reranking endpoint uses TEI's custom format at `/rerank` with query and texts parameters. Token-based dynamic batching is built-in, with configurable `--max-batch-tokens` and `--max-client-batch-size` flags.

TEI's maturity advantage shows in production adoption—it's HuggingFace's official inference solution with 4,200+ GitHub stars and active enterprise deployment. Written in Rust, it delivers excellent memory safety and fast startup times. Known issues include some throughput regressions between versions and AMD CPU compatibility problems with Qwen3 models (GPU deployment recommended).

---

## Infinity delivers single-endpoint elegance with comparable throughput

Infinity's killer feature for this use case is **multi-model serving from a single container**, reducing operational complexity and cold start overhead.

**Combined embedding + reranking deployment:**
```bash
docker run -it --gpus all -v $PWD/data:/app/.cache -p 7997:7997 \
  michaelf34/infinity:latest v2 \
  --model-id BAAI/bge-m3 \
  --model-id BAAI/bge-reranker-v2-m3 \
  --batch-size 32 --batch-size 32 \
  --engine torch --dtype float16 --port 7997
```

The `/models` endpoint reports each model's capabilities (`embed` or `rerank`), and API requests route automatically based on the `model` parameter in the request body. Both `/embeddings` and `/rerank` endpoints are **OpenAI/Cohere-compatible**, enabling straightforward integration with pgvector, LangChain, and standard Python embedding clients.

Infinity's benchmark data from AMD MI300x testing shows **12,000+ embeddings/sec** for short text (3-4 tokens) and **500 embeddings/sec** for long text (512 tokens). Reranking throughput runs approximately 40% slower. The maintainer notes throughput is "similar to TEI" on equivalent GPU hardware.

Important limitation: Infinity supports **dense embeddings only** for bge-m3—sparse retrieval (SPLADE-style) and ColBERT multi-vector modes are not available through Infinity. If you need these hybrid retrieval features, you'd need to use FlagEmbedding's native library directly.

---

## Head-to-head comparison favors Infinity on operational metrics

| Metric | TEI (2 endpoints) | Infinity (1 endpoint) | Winner |
|--------|-------------------|----------------------|--------|
| **Total VRAM** | ~3.5-4.5GB | ~5-8GB | TEI (slightly) |
| **Cold starts** | 2 separate | 1 combined | **Infinity** |
| **Operational complexity** | High (2 deployments) | Low (1 deployment) | **Infinity** |
| **API compatibility** | OpenAI + custom | OpenAI + Cohere | Tie |
| **RunPod template** | Manual setup | Official worker | **Infinity** |
| **Production maturity** | Higher (HuggingFace) | Good (2.6k stars) | TEI |
| **Dynamic batching** | Built-in | Built-in | Tie |
| **Health monitoring** | Prometheus `/metrics` | `/health` endpoint | Tie |

**End-to-end latency breakdown** (estimated for typical RAG query on L4):
- Query embedding: 5-20ms
- Vector retrieval: Application-dependent (pgvector/external)
- Reranking 20 passages: 50-200ms
- **Total inference time**: ~60-220ms

Both servers achieve similar per-request latency. The meaningful difference is **cold start multiplication**—with TEI, you potentially face two cold starts (one per endpoint) versus one with Infinity.

---

## RunPod serverless economics strongly favor single-endpoint architecture

RunPod's serverless pricing for L4 GPUs runs **$0.00019/second (Flex)** or **$0.00013/second (Active)**, equivalent to $0.69/hour and $0.48/hour respectively.

**Cost comparison for 100,000 daily requests:**

| Configuration | Cold Starts | Base Inference | Monthly Estimate |
|---------------|-------------|----------------|------------------|
| Infinity (1 endpoint, Flex) | 1 per burst | 0.5s × 100K × $0.00019 | **~$10-15** |
| TEI (2 endpoints, Flex) | 2 per burst | 0.5s × 100K × 2 × $0.00019 | **~$18-25** |
| Infinity (Active worker) | None | $0.00013/s continuous | ~$350 base + usage |

Cold start impact is significant. With FlashBoot enabled, RunPod achieves sub-200ms cold starts for 48% of requests, but embedding containers with model loading typically take **2-10 seconds** on first invocation. Running two TEI endpoints doubles this penalty during burst traffic.

**The official RunPod worker template** (`runpod/worker-infinity-embedding:1.1.4`) provides immediate deployment capability with environment variable configuration:
```
MODEL_NAMES=BAAI/bge-m3;BAAI/bge-reranker-v2-m3
BATCH_SIZES=32;32
BACKEND=torch
DTYPES=fp16;fp16
```

No official RunPod template exists for TEI—deployment requires custom Docker handler wrapping.

---

## Alternative approaches worth considering

**Can Infinity serve Qwen3-Embedding-0.6B instead of bge-m3?**
Likely yes. Infinity explicitly supports decoder-based models including Alibaba's gte-Qwen2 series, which shares architecture with Qwen3-Embedding. This would give you Qwen3's superior benchmark scores (**64.33 MTEB multilingual** vs bge-m3's 59.56) while maintaining single-endpoint simplicity. Configuration:
```bash
--model-id Qwen/Qwen3-Embedding-0.6B --model-id BAAI/bge-reranker-v2-m3 --engine torch
```

**vLLM for embeddings?**
vLLM 0.8.5+ supports Qwen3-Embedding via `--runner pooling`, but the vLLM team explicitly states embedding support is "for convenience" rather than performance optimization. Combined with vLLM's larger container footprint and lack of native reranking, this approach adds complexity without benefit.

**Triton or Ray Serve?**
Both are enterprise-grade solutions that introduce significant operational complexity (model repository configs, cluster management) without meaningful benefits for this two-model use case. Reserve these for complex multi-model orchestration scenarios.

---

## Final recommendation

**Deploy Configuration 2 (Infinity with bge-m3 + bge-reranker-v2-m3)** using the official RunPod worker template. This delivers the optimal balance of operational simplicity, cost efficiency, and proven compatibility.

If Qwen3-Embedding-0.6B's higher benchmark scores are important for your retrieval quality, modify to use Infinity with Qwen3-Embedding-0.6B + bge-reranker-v2-m3—the architecture supports this configuration, though it receives less explicit testing in documentation.

TEI remains a solid fallback if you encounter Infinity-specific issues, particularly for organizations already standardized on HuggingFace infrastructure. The dual-endpoint complexity is manageable, just more expensive and operationally burdensome for serverless deployment.

| Deployment Path | Effort | Cost Efficiency | Risk Level |
|-----------------|--------|-----------------|------------|
| Infinity + bge-m3 + reranker | Lowest | Best | Low |
| Infinity + Qwen3-Embedding + reranker | Low | Best | Low-Medium |
| TEI dual endpoints | Medium | Moderate | Low |
| vLLM + TEI hybrid | High | Poor | Medium |

For production deployment, start with the Infinity configuration using RunPod's official worker, validate retrieval quality against your benchmark dataset, then iterate on model selection if needed.