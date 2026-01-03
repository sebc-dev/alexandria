# Infinity Embedding Server: Complete Deployment Guide for RunPod L4

Infinity version **0.0.77** (released August 2025) fully supports running embedding and reranking models simultaneously on a single server instance, making it ideal for self-hosted RAG systems. The server is OpenAI API-compatible and handles both **Qwen3-Embedding-0.6B** and **bge-reranker-v2-m3**—though Qwen3 requires updated transformers. An L4 GPU with 24GB VRAM provides ample headroom for both models with aggressive batching.

## Current release and breaking changes

The latest stable release is **v0.0.77** from August 22, 2025, available via Docker image `michaelf34/infinity:latest`. Three breaking changes affect recent deployments: version 0.0.70 **dropped Pydantic 1.x support** (now requires Pydantic 2.x), version 0.0.75 made the CLI default to the `v2` command, and version 0.0.76 broke compatibility between `torch.compile` and BetterTransformers with torch 2.6.0.

Key recent additions include **ModernBERT support** (0.0.74), **Matryoshka embeddings** (0.0.73), AWS Neuron support (0.0.72), and ColBERT late-interaction models (0.0.68). The project maintains active development with ~80 releases since October 2023 and 39 contributors.

## Model compatibility assessment

**bge-reranker-v2-m3 is explicitly tested and validated** in the official repository's README under "Tested reranking models." No special configuration needed—it works out of the box with the torch engine.

**Qwen3-Embedding-0.6B requires extra steps.** GitHub issue #642 documents a `KeyError: 'qwen3'` error because the model type wasn't recognized by the bundled transformers library. The fix requires transformers≥4.51.0, which you can install via `pip install git+https://github.com/huggingface/transformers.git`. Alternatively, the Qwen2-based `Alibaba-NLP/gte-Qwen2-7B-instruct` models are fully supported without modification if Qwen3 proves problematic.

Multi-model orchestration is a core feature. The official documentation shows embedding + reranking deployments achieving **487-12,021 embeddings/second** and **up to 4,836 rerank requests/second** simultaneously.

## Configuration for Qwen embedding and BGE reranker

### Docker deployment command

```bash
docker run -it --gpus all \
  -v $PWD/data:/app/.cache \
  -p 7997:7997 \
  michaelf34/infinity:latest \
  v2 \
  --model-id Qwen/Qwen3-Embedding-0.6B \
  --model-id BAAI/bge-reranker-v2-m3 \
  --batch-size 64 \
  --batch-size 32 \
  --dtype float16 \
  --device cuda \
  --engine torch \
  --port 7997
```

### Environment variable configuration

All CLI arguments convert to environment variables with the `INFINITY_` prefix in UPPER_SNAKE_CASE. Multiple models use semicolon separation:

```bash
INFINITY_MODEL_ID="Qwen/Qwen3-Embedding-0.6B;BAAI/bge-reranker-v2-m3;"
INFINITY_BATCH_SIZE="64;32;"
INFINITY_DTYPE="float16"
INFINITY_DEVICE="cuda"
INFINITY_ENGINE="torch"
INFINITY_PORT=7997
INFINITY_API_KEY="your-secret-key"
INFINITY_ANONYMOUS_USAGE_STATS="0"
HF_HOME="/app/.cache"
```

### Docker Compose example

```yaml
version: '3.8'
services:
  infinity:
    image: michaelf34/infinity:latest
    ports:
      - "7997:7997"
    environment:
      - INFINITY_MODEL_ID=Qwen/Qwen3-Embedding-0.6B;BAAI/bge-reranker-v2-m3;
      - INFINITY_BATCH_SIZE=64;32;
      - INFINITY_DTYPE=float16
      - INFINITY_API_KEY=your-secret-key
    command: ["v2", "--port", "7997"]
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
    volumes:
      - ./cache:/app/.cache
```

## Optimal batch sizes and GPU memory for L4

The L4 GPU's **24GB VRAM** comfortably handles both models. Qwen3-Embedding-0.6B (~600M parameters) requires approximately **1.2-1.5GB** in FP16, while bge-reranker-v2-m3 (~568M parameters) needs approximately **1.5-2GB**. Combined overhead with CUDA runtime totals roughly **5-6GB**, leaving ~18GB for batch processing.

| Model | Recommended Batch Size | VRAM Usage |
|-------|----------------------|------------|
| Qwen3-Embedding-0.6B | 64-128 | ~3-5GB with batches |
| bge-reranker-v2-m3 | 32-64 | ~3-4GB with batches |

Increase batch size in multiples of 8 for optimal GPU utilization. Start with `--batch-size 64 --batch-size 32` and scale up if memory permits. Infinity uses **dynamic batching**—requests queue automatically and tokenization runs in dedicated worker threads.

## API endpoints and request formats

### Endpoint paths

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/v1/embeddings` | POST | OpenAI-compatible embeddings |
| `/embeddings` | POST | Native embeddings endpoint |
| `/rerank` | POST | Document reranking |
| `/v1/models` | GET | List loaded models |
| `/health` | GET | Health check |
| `/docs` | GET | Swagger UI |

### Embedding request example

```bash
curl -X POST http://localhost:7997/v1/embeddings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-secret-key" \
  -d '{
    "model": "Qwen/Qwen3-Embedding-0.6B",
    "input": ["Technical documentation query", "Another query to embed"]
  }'
```

### Embedding response structure

```json
{
  "object": "list",
  "model": "Qwen/Qwen3-Embedding-0.6B",
  "data": [
    {"object": "embedding", "embedding": [0.01, -0.02, ...], "index": 0},
    {"object": "embedding", "embedding": [0.04, -0.05, ...], "index": 1}
  ],
  "usage": {"prompt_tokens": 8, "total_tokens": 8}
}
```

### Reranking request example

```bash
curl -X POST http://localhost:7997/rerank \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-secret-key" \
  -d '{
    "model": "BAAI/bge-reranker-v2-m3",
    "query": "How to configure Kubernetes pods?",
    "documents": [
      "Kubernetes pods are configured via YAML manifests...",
      "Docker containers run isolated processes...",
      "Pod specifications include resource limits..."
    ],
    "return_documents": false
  }'
```

### Reranking response structure

```json
{
  "model": "BAAI/bge-reranker-v2-m3",
  "results": [
    {"index": 0, "relevance_score": 0.89},
    {"index": 2, "relevance_score": 0.74},
    {"index": 1, "relevance_score": 0.12}
  ]
}
```

Results are ordered by relevance score (highest first). The `index` field maps back to the original document array position.

## OpenAI client compatibility

```python
from openai import OpenAI

client = OpenAI(
    api_key="your-secret-key",
    base_url="http://localhost:7997/v1"
)

embeddings = client.embeddings.create(
    model="Qwen/Qwen3-Embedding-0.6B",
    input=["What is Kubernetes?", "Docker container basics"]
)

for item in embeddings.data:
    print(f"Index {item.index}: {len(item.embedding)} dimensions")
```

## Health monitoring and operational endpoints

The `/health` endpoint returns server status and model readiness. For Kubernetes deployments:

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 7997
  initialDelaySeconds: 60
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /health
    port: 7997
  initialDelaySeconds: 30
  periodSeconds: 5
```

Check loaded models via `GET /v1/models`:

```json
{
  "data": [
    {"id": "Qwen/Qwen3-Embedding-0.6B", "stats": {"batch_size": 64}},
    {"id": "BAAI/bge-reranker-v2-m3", "stats": {"batch_size": 32}}
  ]
}
```

## RunPod serverless deployment

RunPod provides a dedicated worker image: `runpod/worker-infinity-embedding:stable-cuda12.1.0`. Configure via environment variables:

```bash
MODEL_NAMES="Qwen/Qwen3-Embedding-0.6B;BAAI/bge-reranker-v2-m3;"
BATCH_SIZES="64;32;"
BACKEND="torch"
DTYPES="float16;float16;"
INFINITY_QUEUE_SIZE=48000
RUNPOD_MAX_CONCURRENCY=300
```

### RunPod API call patterns

**OpenAI-compatible endpoint:**
```bash
curl -H "Authorization: Bearer $RUNPOD_API_KEY" \
  https://api.runpod.ai/v2/<ENDPOINT_ID>/openai/v1/embeddings \
  -d '{"model":"Qwen/Qwen3-Embedding-0.6B","input":"Hello world"}'
```

**Standard RunPod endpoint:**
```bash
curl -X POST \
  -H "Authorization: Bearer $RUNPOD_API_KEY" \
  -d '{"input":{"model":"BAAI/bge-reranker-v2-m3","query":"test","documents":["doc1","doc2"]}}' \
  https://api.runpod.ai/v2/<ENDPOINT_ID>/runsync
```

## Performance tuning recommendations

For an L4 GPU running a RAG workload with technical documentation:

- **Engine**: Use `--engine torch` for maximum model compatibility
- **Precision**: `--dtype float16` balances speed and accuracy
- **Warmup**: Add `--model-warmup` to pre-compile models before serving requests
- **Authentication**: Always set `--api-key` for production deployments
- **Cache**: Mount `/app/.cache` to persistent storage to avoid re-downloading models on container restart

If encountering the Qwen3 `KeyError`, either build a custom Docker image with updated transformers or use the fallback `Alibaba-NLP/gte-multilingual-base` (559M params) which provides excellent multilingual embedding performance with native support.

## Conclusion

Infinity 0.0.77 provides production-ready infrastructure for combined embedding and reranking workloads. The L4's 24GB VRAM handles Qwen3-Embedding-0.6B and bge-reranker-v2-m3 simultaneously with generous batch sizes. Key deployment considerations: verify Qwen3 compatibility by testing with updated transformers first, use semicolon-separated environment variables for multi-model RunPod serverless configs, and start with batch sizes of 64/32 before scaling up based on actual memory utilization. The OpenAI-compatible API enables drop-in replacement for existing RAG pipelines without client-side code changes.