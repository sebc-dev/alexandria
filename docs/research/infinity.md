# Infinity embedding server: multi-model GPU deployment guide

Infinity is an MIT-licensed, high-throughput embedding and reranking server that **can serve both embedding and reranking models simultaneously from a single endpoint**. This makes it ideal for RAG pipelines requiring both semantic search and result reranking. The server supports Qwen3-Embedding-0.6B + bge-reranker-v2-m3 on a single GPU endpoint, though Qwen3 models require a transformers library update due to a recent architecture addition. For production deployment, expect **~3-4 GB combined VRAM** for these models with comfortable operation on 8+ GB GPUs.

## What is Infinity and who maintains it

Infinity is a REST API serving engine designed specifically for text embeddings, reranking, CLIP, CLAP, and ColPali models. Created and maintained by **Michael Feil** (@michaelfeil), the project has reached significant maturity with **986 commits**, **80 releases**, and strong community adoption (**~2,600 stars**, **177 forks**, **39 contributors**).

The project is released under the **MIT License** and remains actively maintained. The latest release is **v0.0.77** (August 22, 2025), with recent updates including Blackwell GPU support (July 2025) and AMD/CPU/ONNX Docker images (November 2024). Official resources include the GitHub repository at https://github.com/michaelfeil/infinity, documentation at https://michaelfeil.github.io/infinity, and a live Swagger demo at https://infinity.modal.michaelfeil.eu/docs.

## Multi-model architecture enables simultaneous embedding and reranking

Infinity's core differentiator is its `AsyncEngineArray` architecture, which orchestrates multiple models within a single process. The server automatically detects model capabilities—using `engine.embed()` for embeddings and `engine.rerank()` for cross-encoders—routing requests appropriately based on the `model` parameter in each API call.

**Loading multiple models** uses CLI v2 syntax (available since v0.0.34):

```bash
docker run -it --gpus all \
  -v $PWD/cache:/app/.cache \
  -p 7997:7997 \
  michaelf34/infinity:latest \
  v2 \
  --model-id Qwen/Qwen3-Embedding-0.6B \
  --model-id BAAI/bge-reranker-v2-m3 \
  --batch-size 16 \
  --batch-size 8 \
  --port 7997
```

Alternatively, use environment variables with semicolon-separated values: `INFINITY_MODEL_ID="Qwen/Qwen3-Embedding-0.6B;BAAI/bge-reranker-v2-m3;"` and `INFINITY_BATCH_SIZE="16;8;"`.

**Model compatibility note**: While bge-reranker-v2-m3 is explicitly tested and confirmed working, Qwen3-Embedding-0.6B requires `transformers>=4.51.0` due to its new architecture type. GitHub Issue #642 documents a `KeyError: 'qwen3'` error with older transformers versions. The workaround is updating to the latest transformers: `pip install git+https://github.com/huggingface/transformers.git`, or using a custom Docker image with the updated library.

## OpenAI-compatible API with dedicated reranking endpoint

The API is fully **OpenAI-compatible**, working directly with the official OpenAI Python SDK. For multi-model deployments, the `model` parameter in each request specifies which loaded model handles the request.

**Embeddings endpoint** (`POST /embeddings` or `POST /v1/embeddings`):

```json
// Request
{
  "model": "Qwen/Qwen3-Embedding-0.6B",
  "input": ["Text to embed", "Another document"],
  "encoding_format": "float"
}

// Response
{
  "object": "list",
  "model": "Qwen/Qwen3-Embedding-0.6B",
  "data": [
    {"object": "embedding", "embedding": [0.01, -0.02, ...], "index": 0}
  ],
  "usage": {"prompt_tokens": 8, "total_tokens": 8}
}
```

**Reranking endpoint** (`POST /rerank`):

```json
// Request
{
  "model": "BAAI/bge-reranker-v2-m3",
  "query": "What is machine learning?",
  "documents": ["ML is a subset of AI...", "The weather is nice today"],
  "return_documents": false
}

// Response
{
  "results": [
    {"index": 0, "relevance_score": 0.95},
    {"index": 1, "relevance_score": 0.12}
  ]
}
```

**Using with OpenAI SDK**:

```python
from openai import OpenAI

client = OpenAI(api_key="EMPTY", base_url="http://localhost:7997/v1")
embeddings = client.embeddings.create(
    model="Qwen/Qwen3-Embedding-0.6B",
    input="What is deep learning?"
).data[0].embedding
```

The API also supports LiteLLM (Cohere-style rerank), LangChain's `InfinityEmbeddings` class, and dynamic batching—automatically queuing and batching requests to maximize GPU utilization.

## Performance benchmarks show competitive throughput

Infinity achieves throughput **comparable to HuggingFace text-embeddings-inference** while supporting a broader range of model types. Key benchmark results demonstrate its capabilities across different hardware:

| Configuration | Short Sequences | Long Sequences (512 tokens) |
|--------------|-----------------|----------------------------|
| AMD MI300x (ONNX) | **12,021 embeddings/sec** | 487 embeddings/sec |
| Reranking (MI300x) | 4,836 requests/sec | 242 requests/sec |
| Inference latency | 2.22 ms | 57.67 ms |

On **NVIDIA L4** with bge-large-en-v1.5 processing 256 sentences per request:
- Infinity (torch + compile + FA2): **0.51 requests/sec**
- HuggingFace TEI (flashbert): 0.54 requests/sec
- SentenceTransformers baseline: 0.17 requests/sec

The server claims support for **up to 1,000 concurrent RAG users** with short queries on a single replica. VRAM optimization strategies include adjustable batch sizes, multi-GPU distribution (`--device-id 0,1,2,3`), and engine selection based on workload characteristics.

## Docker images and deployment configurations

Infinity provides official Docker images on Docker Hub under `michaelf34/infinity`:

| Image Tag | Use Case |
|-----------|----------|
| `latest` | NVIDIA CUDA GPUs |
| `latest-cpu` | CPU-only inference |
| `latest-rocm` | AMD ROCm (MI200/MI300) |
| `latest-trt-onnx` | TensorRT/ONNX with flash-attention |

**Complete docker-compose for dual-model deployment**:

```yaml
version: '3.8'
services:
  infinity-embedding:
    image: michaelf34/infinity:latest
    container_name: infinity-multi-model
    restart: unless-stopped
    ports:
      - "7997:7997"
    volumes:
      - ./model-cache:/app/.cache
    environment:
      - HF_HOME=/app/.cache
    command:
      - "v2"
      - "--model-id"
      - "Qwen/Qwen3-Embedding-0.6B"
      - "--model-id"
      - "BAAI/bge-reranker-v2-m3"
      - "--batch-size"
      - "16"
      - "--batch-size"
      - "8"
      - "--port"
      - "7997"
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
```

## RunPod serverless deployment setup

RunPod offers a dedicated worker image at `runpod/worker-infinity-embedding:1.1.4` using Infinity 0.0.76 internally. Configure via environment variables:

| Variable | Value |
|----------|-------|
| `MODEL_NAMES` | `Qwen/Qwen3-Embedding-0.6B;BAAI/bge-reranker-v2-m3` |
| `BATCH_SIZES` | `16;8` |
| `BACKEND` | `torch` |
| `DTYPES` | `auto;auto` |

**API calls to RunPod endpoint**:

```bash
# Embeddings
curl -X POST \
  -H "Authorization: Bearer <API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"input":{"model":"Qwen/Qwen3-Embedding-0.6B","input":"Hello world"}}' \
  https://api.runpod.ai/v2/<ENDPOINT_ID>/runsync

# Reranking
curl -X POST \
  -H "Authorization: Bearer <API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"input":{"model":"BAAI/bge-reranker-v2-m3","query":"Search query","docs":["doc1","doc2"]}}' \
  https://api.runpod.ai/v2/<ENDPOINT_ID>/runsync
```

For cold start mitigation, RunPod's **Active Workers** provide always-on instances with zero cold start (30% discount, continuous billing), while **Flex Workers** scale to zero but incur model loading latency. Network volumes help persist model weights across worker instances.

## Cost analysis favors single multi-model endpoint

Running both Qwen3-Embedding-0.6B (~1.5 GB) and bge-reranker-v2-m3 (~1.5 GB) on a single endpoint requires approximately **6-8 GB VRAM** with comfortable headroom for batch processing and KV cache.

**Single endpoint approach** (recommended):
- Combined VRAM: ~3-4 GB base + runtime overhead
- Suitable GPUs: RTX 4060 (8GB), RTX 4070 (12GB), A10 (24GB)
- RunPod cost: **~$0.31-0.44/hr** (A10 or RTX 4090)

**Two separate endpoints**:
- Each model on dedicated GPU
- Double the infrastructure cost: **~$0.62-0.88/hr**
- Only justified if models have drastically different scaling requirements

| GPU | VRAM | RunPod Flex Price |
|-----|------|------------------|
| RTX 4060 | 8 GB | ~$0.20/hr |
| A10 | 24 GB | ~$0.31/hr |
| RTX 4090 | 24 GB | ~$0.44/hr |
| A100 | 40 GB | ~$1.09/hr |

The single-endpoint strategy reduces costs by **40-50%** while simplifying API management. Cold starts load both models simultaneously, taking approximately 30-60 seconds depending on network speed and model sizes.

## Conclusion

Infinity provides a mature, production-ready solution for serving multiple embedding and reranking models from a single GPU endpoint. The key technical considerations for deploying Qwen3-Embedding-0.6B + bge-reranker-v2-m3 are ensuring the transformers library is updated to support Qwen3 architecture, allocating 8+ GB VRAM for comfortable operation, and using the v2 CLI syntax with repeated `--model-id` flags. The OpenAI-compatible API simplifies integration while the single-endpoint architecture reduces infrastructure costs by approximately half compared to separate deployments. For RunPod serverless deployments, the official worker image handles most configuration automatically through environment variables.