# TDD Testing Patterns for RAG and Vector Search Systems

The fundamental insight for testing RAG systems is that traditional TDD must evolve into **Evaluation-Driven Development (EDD)**—a paradigm shift where threshold-based semantic evaluation replaces exact-match assertions. This approach, championed by frameworks like RAGAS and DeepEval, recognizes that non-deterministic LLM outputs require statistical and semantic validation rather than binary pass/fail testing. The most effective RAG testing strategies separate retrieval evaluation from generation evaluation, using distinct metrics for each component while maintaining end-to-end integration tests that verify the complete pipeline.

## The evaluation-driven development paradigm replaces exact matching

Traditional TDD's Red-Green-Refactor cycle requires adaptation for RAG systems because LLM outputs vary even with identical inputs—**temperature=0 does not guarantee determinism** due to floating-point arithmetic, GPU non-deterministic operations, and Mixture-of-Experts routing. Research from academic papers confirms that models are rarely 100% deterministic even at temperature zero.

The adapted EDD cycle works as follows: write a failing test with **threshold-based assertions** (e.g., faithfulness > 0.8), implement the RAG component until metrics pass, then refactor to optimize performance. DeepEval provides the most mature implementation of this pattern with pytest integration:

```python
from deepeval import assert_test
from deepeval.metrics import FaithfulnessMetric, AnswerRelevancyMetric
from deepeval.test_case import LLMTestCase

def test_rag_faithfulness():
    test_case = LLMTestCase(
        input="What is the return policy?",
        actual_output="We offer 30-day returns at no cost.",
        retrieval_context=["All customers are eligible for 30 day full refund."]
    )
    assert_test(test_case, [
        FaithfulnessMetric(threshold=0.8),
        AnswerRelevancyMetric(threshold=0.7)
    ])
```

The critical architectural principle is **separating retrieval tests from generation tests** to isolate failure modes. When a RAG system produces incorrect answers, you need to know whether the retriever failed to find relevant documents or the generator hallucinated despite having correct context. This separation enables targeted debugging and optimization.

## Testing vector databases requires in-memory modes and deterministic fixtures

Each major vector database provides testing-friendly configurations that eliminate external dependencies while preserving full functionality. **Qdrant's `:memory:` mode** offers the simplest approach—a single line creates an ephemeral in-memory instance requiring no Docker. **Pinecone Local** provides a Docker-based emulator supporting up to 100,000 records per index. **Chroma's EphemeralClient** resets automatically between test runs. For PostgreSQL-based pgvector, **Testcontainers** or pytest-postgresql provide isolated test databases with the vector extension pre-installed.

The fundamental challenge in vector search testing is **determinism for non-deterministic embeddings**. Three strategies address this:

- **Pre-computed embedding fixtures**: Store embeddings alongside test code and load them directly, bypassing embedding model calls entirely
- **VCR-style recording**: Use pytest-vcr to record HTTP API calls on first run, replaying them deterministically on subsequent runs
- **Deterministic seed embeddings**: For local models, set random seeds across Python, NumPy, and PyTorch to achieve reproducible outputs (though API-based embeddings cannot be seeded)

Testing HNSW index quality requires comparing approximate nearest neighbor results against brute-force ground truth. The key metric is **recall**—the percentage of true neighbors found by the approximate index. Production HNSW configurations should achieve **≥95% recall** against brute-force search. The critical parameters are `efConstruction` (graph build quality, typically 128-512), `M` (connectivity, typically 16-64), and `efSearch` (query-time accuracy, typically 100-2000).

## Mocking LLM calls strategically separates unit from integration tests

The decision between mocked and real LLM calls follows a clear pattern: **unit tests always mock**, **integration tests selectively use real calls**, and **evaluation tests require real LLMs** for meaningful quality assessment. This layered approach balances test speed and cost against evaluation fidelity.

For OpenAI specifically, the `openai-responses` library provides decorator-based mocking that intercepts API calls:

```python
import openai_responses

@openai_responses.mock.embeddings(embedding=[0.1]*1536)
def test_embedding_pipeline():
    client = OpenAI(api_key="fake-key")
    result = client.embeddings.create(model="text-embedding-ada-002", input="test")
    assert len(result.data[0].embedding) == 1536
```

LangChain provides `GenericFakeChatModel` for mocking chat completions with predetermined responses including tool calls. The `pytest-mockllm` package extends this pattern to Anthropic and Gemini. For maximum determinism in CI/CD, VCR.py records complete HTTP interactions as "cassettes" that replay identically across runs—though cassettes must be regenerated whenever prompts change.

Snapshot testing for LLM outputs requires **semantic comparison** rather than string matching. DeepEval's G-Eval metric evaluates semantic similarity between actual and expected outputs using an LLM judge:

```python
from deepeval.metrics import GEval

similarity_metric = GEval(
    name="Similarity",
    criteria="Determine if actual output is semantically equivalent to expected output.",
    evaluation_params=[LLMTestCaseParams.ACTUAL_OUTPUT, LLMTestCaseParams.EXPECTED_OUTPUT]
)
```

## Retrieval quality metrics form the foundation of RAG evaluation

The standard information retrieval metrics provide objective measurement of retriever performance independent of generation quality:

| Metric | Purpose | Use When |
|--------|---------|----------|
| **Precision@k** | Share of relevant items in top k results | False positives are costly |
| **Recall@k** | Share of all relevant items found in top k | Missing relevant items is costly |
| **MRR** | Average position of first relevant result | Only top result matters |
| **nDCG@k** | Ranking quality with graded relevance | Relevance has degrees (1-5 scale) |
| **Hit Rate** | Whether any relevant item appears in top k | Single-answer queries |

LlamaIndex provides `RetrieverEvaluator` for systematic measurement against labeled datasets:

```python
from llama_index.core.evaluation import RetrieverEvaluator

evaluator = RetrieverEvaluator.from_metric_names(
    ["mrr", "hit_rate", "precision", "recall"],
    retriever=retriever
)
results = await evaluator.aevaluate_dataset(qa_dataset)
```

Generation quality requires **LLM-as-judge evaluation** using the RAG Triad: context relevance (are retrieved chunks relevant?), groundedness (is the answer supported by context?), and answer relevance (does the response address the query?). RAGAS pioneered reference-free evaluation where LLMs assess these dimensions without human-annotated ground truth—though context recall still requires expected answers for comparison.

## Golden datasets enable regression testing across RAG changes

The most reliable regression testing uses **golden datasets**—curated question-answer pairs verified by subject matter experts. The recommended creation pipeline starts with **silver datasets** (LLM-generated QA pairs from your document corpus), then curates a **gold subset** through human review. Microsoft's Data Science team recommends **minimum 20 questions for personal projects**, **100+ for enterprise deployments**.

RAGAS provides automated test generation:

```python
from ragas.testset import TestsetGenerator

generator = TestsetGenerator(llm=llm, embedding_model=embeddings)
dataset = generator.generate_with_langchain_docs(
    documents,
    testset_size=50,
    distributions={"simple": 0.4, "reasoning": 0.3, "multi_context": 0.2, "conditional": 0.1}
)
```

Regression testing compares current metric scores against established baselines with defined tolerance (typically 5%):

```python
def check_regression(current_metrics, baseline, tolerance=0.05):
    regressions = []
    for metric, value in current_metrics.items():
        if value < baseline[metric] * (1 - tolerance):
            regressions.append(f"{metric}: {value:.3f} < {baseline[metric]:.3f}")
    return regressions
```

## The 2024-2025 RAG evaluation framework landscape has matured significantly

**RAGAS** remains the foundational open-source framework, introduced at EACL 2024, providing reference-free evaluation with four core metrics (faithfulness, answer relevancy, context precision, context recall). It integrates natively with LangChain, LlamaIndex, and observability platforms.

**DeepEval** offers the most CI/CD-ready experience with 50+ metrics, pytest-style assertions, and parallel test execution. Its `@observe` decorator enables component-level tracing, and the Confident AI cloud platform provides dashboards for test results and annotations.

**LangSmith** provides deep integration with the LangChain ecosystem, featuring online evaluations that automatically run LLM judges on production traces, intermediate step evaluation for inspecting RAG pipeline stages, and dataset versioning for systematic experiments.

**TruLens** (now Snowflake) provides the "RAG Triad" framework with OpenTelemetry integration for trace-based evaluation. **Phoenix/Arize** offers open-source observability built entirely on OpenTelemetry, enabling vendor-agnostic tracing with built-in RAG evaluators. **promptfoo** serves developer-first prompt testing with declarative YAML configuration, matrix evaluation views, and 50+ red-teaming vulnerability types.

For framework selection: use **RAGAS for quick open-source evaluation**, **DeepEval for CI/CD-first testing**, **LangSmith for LangChain ecosystems**, **Phoenix for deep observability**, and **promptfoo for prompt iteration**.

## CI/CD integration requires cost management and quality gates

RAG evaluation in CI/CD faces the dual challenge of test reliability and API cost management. The recommended pattern uses **tiered testing**: fast unit tests with mocked dependencies run on every commit, integration tests with real embeddings run on PRs, and full evaluation suites with real LLM judges run on main branch merges.

```yaml
name: RAG Tests
on: [push, pull_request]
jobs:
  unit:
    runs-on: ubuntu-latest
    steps:
      - run: pytest tests/unit -v --ignore=tests/integration
  
  evaluation:
    needs: unit
    if: github.ref == 'refs/heads/main'
    steps:
      - env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: deepeval test run tests/rag_tests.py
```

Cost management strategies include using cheaper models (GPT-3.5-turbo instead of GPT-4) for evaluation judges, aggressive caching of embedding and LLM calls, batch evaluation rather than per-trace evaluation, and running full evaluation only on significant changes. Quality gates should enforce **minimum thresholds** (e.g., faithfulness > 0.8, relevance > 0.7) and fail pipelines when scores drop below baseline.

## Chunking and context assembly require empirical testing

Document chunking significantly impacts retrieval quality, with **512-1024 token chunks** performing well for most use cases according to NVIDIA research. Testing chunking strategies requires evaluating the same query set across different configurations:

```python
@pytest.mark.parametrize("chunk_size", [128, 256, 512, 1024, 2048])
def test_chunk_size_impact(chunk_size, test_queries, ground_truth):
    chunks = splitter.split_documents(documents, chunk_size=chunk_size)
    vectorstore = Chroma.from_documents(chunks, embeddings)
    
    precision_scores = []
    for query, expected in zip(test_queries, ground_truth):
        retrieved = vectorstore.similarity_search(query, k=5)
        precision_scores.append(calculate_precision(retrieved, expected))
    
    assert np.mean(precision_scores) > 0.7
```

Context assembly tests verify that assembled context fits within model limits (leaving room for system prompt and response), maintains relevance ordering, and removes duplicate content. Hybrid search testing validates that reciprocal rank fusion properly combines vector and keyword results—Anthropic's contextual retrieval research recommends retrieving **150 initial chunks**, then using a reranker to select the **top 20** for final context.

## Conclusion: Effective RAG testing demands evaluation-driven development

The key insights for production RAG testing are: adopt EDD with threshold-based semantic assertions; separate retrieval and generation evaluation to isolate failures; use in-memory vector database modes for fast unit tests while reserving real databases for integration tests; leverage golden datasets for regression testing with clear baseline metrics; and integrate evaluation into CI/CD with cost-aware tiering.

The field has standardized around **faithfulness, answer relevance, context precision, and context recall** as core metrics, with RAGAS, DeepEval, and LangSmith providing mature implementations. The most important architectural decision is maintaining clear boundaries between retrieval and generation components, enabling independent testing and optimization of each layer while preserving end-to-end validation of the complete system.