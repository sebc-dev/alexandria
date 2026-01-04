# Langchain4j works seamlessly with Infinity embedding server

**Your proposed approach is correct and will work.** Langchain4j's `OpenAiEmbeddingModel` fully supports custom base URLs, arbitrary model names, and batch embeddings—making it compatible with OpenAI-compatible servers like Infinity. One minor adjustment: append `/v1` to your base URL, and consider HTTP/1.1 configuration if you encounter connection issues.

## Your code pattern needs one small fix

The code you provided is nearly correct. Here's the validated and complete version:

```java
EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .baseUrl("http://runpod-infinity:8080/v1")  // Note: add /v1 suffix
    .apiKey("runpod-api-key")                    // Bearer token auth
    .modelName("BAAI/bge-m3")                    // HuggingFace model ID works
    .dimensions(1024)                            // Optional: specify dimensions
    .maxSegmentsPerBatch(32)                     // Optional: control batch size
    .timeout(Duration.ofSeconds(60))             // Optional: adjust timeout
    .build();
```

**Critical note on version**: You mentioned version 1.0.1, but the current stable release as of January 2026 is **1.10.0**. Verify your Maven dependency uses the correct version:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.10.0</version>
</dependency>
```

## All five questions answered

**1. Custom Base URL Support**: Yes, `OpenAiEmbeddingModel` includes a `baseUrl(String)` builder method. This is explicitly designed for OpenAI-compatible servers and is documented at docs.langchain4j.dev. The default is `https://api.openai.com/v1`, but any URL works.

**2. Base URL Configuration**: Use `.baseUrl("http://your-server:port/v1")`. The `/v1` path segment is important because Infinity exposes endpoints at `/v1/embeddings`. Your Infinity server at `http://runpod-infinity:8080` should be configured as `http://runpod-infinity:8080/v1`.

**3. Batch Embeddings Support**: Yes, `embedAll(List<TextSegment>)` is the primary batch embedding method. It returns `Response<List<Embedding>>`. Control batch sizes with `.maxSegmentsPerBatch(Integer)` in the builder:

```java
Response<List<Embedding>> embeddings = embeddingModel.embedAll(textSegments);
```

**4. Authentication Configuration**: The `.apiKey("your-key")` method is sufficient—it configures Bearer token authentication in the Authorization header automatically. For Infinity servers without authentication configured, use any placeholder string like `"EMPTY"` or `"none"`.

**5. Model Name Mapping**: Arbitrary model names work without restriction. The `modelName()` method accepts both the `OpenAiEmbeddingModelName` enum (for OpenAI models) and raw `String` values (for custom models). **No validation blocks custom names** like `"BAAI/bge-m3"`. Infinity expects HuggingFace model IDs, which aligns perfectly.

## HTTP protocol consideration for Infinity

Infinity uses FastAPI, which typically serves HTTP/1.1. Langchain4j defaults to HTTP/2, which can cause connection issues with some servers. If you encounter problems, configure HTTP/1.1 explicitly:

```java
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import java.net.http.HttpClient;

HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1);

JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
    .httpClientBuilder(httpClientBuilder);

EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .baseUrl("http://runpod-infinity:8080/v1")
    .apiKey("runpod-api-key")
    .modelName("BAAI/bge-m3")
    .httpClientBuilder(jdkHttpClientBuilder)
    .build();
```

This requires an additional dependency:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-http-client-jdk</artifactId>
    <version>1.10.0</version>
</dependency>
```

## BGE-M3 on Infinity has one limitation

Infinity supports **BAAI/bge-m3** but only for **dense embeddings**. The sparse retrieval and ColBERT multi-vector features of BGE-M3 are not exposed through the OpenAI-compatible API. For your RAG use case with 1024-dimension dense vectors, this is fine—dense embeddings work perfectly.

## Spring Boot configuration alternative

If your Alexandria RAG Server uses Spring Boot, configure via `application.properties`:

```properties
langchain4j.open-ai.embedding-model.base-url=http://runpod-infinity:8080/v1
langchain4j.open-ai.embedding-model.api-key=${RUNPOD_API_KEY}
langchain4j.open-ai.embedding-model.model-name=BAAI/bge-m3
langchain4j.open-ai.embedding-model.dimensions=1024
langchain4j.open-ai.embedding-model.timeout=60s
langchain4j.open-ai.embedding-model.max-retries=3
```

This auto-configures an `EmbeddingModel` bean for injection.

## Complete builder method reference

The `OpenAiEmbeddingModel.builder()` provides these configuration options:

| Method | Purpose |
|--------|---------|
| `baseUrl(String)` | Custom endpoint URL |
| `apiKey(String)` | Bearer token authentication |
| `modelName(String)` | Model identifier |
| `dimensions(Integer)` | Output embedding dimensions |
| `maxSegmentsPerBatch(Integer)` | Batch size limit |
| `timeout(Duration)` | Request timeout |
| `maxRetries(Integer)` | Retry count on failure |
| `httpClientBuilder(HttpClientBuilder)` | Custom HTTP client |
| `logRequests(Boolean)` | Debug request logging |
| `logResponses(Boolean)` | Debug response logging |
| `customHeaders(Map)` | Additional HTTP headers |

## No alternative approach needed

Your primary approach is correct—no need for a custom `EmbeddingModel` implementation or Infinity-specific integration. Langchain4j doesn't have a dedicated Infinity module, but the standard `langchain4j-open-ai` module with `baseUrl` configuration is the intended solution for all OpenAI-compatible servers. This same pattern works for Ollama, vLLM, LocalAI, Groq, DeepSeek, and others.

## Verification test

To validate the setup before full integration:

```java
EmbeddingModel model = OpenAiEmbeddingModel.builder()
    .baseUrl("http://runpod-infinity:8080/v1")
    .apiKey("runpod-api-key")
    .modelName("BAAI/bge-m3")
    .logRequests(true)
    .logResponses(true)
    .build();

Response<Embedding> response = model.embed("Test embedding request");
System.out.println("Dimensions: " + response.content().dimension());
System.out.println("Vector sample: " + response.content().vector()[0]);
```

Enable `logRequests` and `logResponses` during development to debug any API communication issues.