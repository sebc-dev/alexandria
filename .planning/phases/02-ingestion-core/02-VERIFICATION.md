---
phase: 02-ingestion-core
verified: 2026-01-20T11:35:00Z
status: passed
score: 5/5 must-haves verified
---

# Phase 02: Ingestion Core Verification Report

**Phase Goal:** Les fichiers markdown sont indexes avec embeddings et metadata stockes
**Verified:** 2026-01-20T11:35:00Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Un repertoire de fichiers markdown est parse et les documents sont crees en base | VERIFIED | `IngestionService.ingestDirectory()` walks directory, filters `.md` files, calls `ingestFile()` for each. `JdbcDocumentRepository.save()` inserts into `documents` table. Integration test `ingestsDirectory()` verifies 2 files ingested. |
| 2 | Chaque document est chunke en parent (1000 tokens) et children (200 tokens) | VERIFIED | `HierarchicalChunker` uses `PARENT_TOKENS = 1000`, `CHILD_TOKENS = 200` with `CHARS_PER_TOKEN = 4` approximation. Two-pass splitting: `parentSplitter` (4000 chars) then `childSplitter` (800 chars) per parent. `ChunkPair` links parent to children. |
| 3 | Les embeddings all-MiniLM-L6-v2 sont generes pour chaque chunk | VERIFIED | `LangChain4jEmbeddingGenerator` instantiates `AllMiniLmL6V2EmbeddingModel` (line 32). `IngestionService.ingestFile()` calls `embeddingGenerator.embed()` for both parent (line 159) and child chunks (line 173). |
| 4 | Les metadonnees (titre, tags, categorie) sont extraites du frontmatter YAML | VERIFIED | `MarkdownParser` uses CommonMark `YamlFrontMatterExtension`. `extractMetadata()` pulls `title`, `category`, `tags` fields (lines 55-59). `DocumentMetadata` record holds structured output. Tests verify extraction. |
| 5 | Les embeddings sont stockes dans pgvector avec index HNSW | VERIFIED | Schema `002-schema.sql` defines `embedding vector(384)` column (line 39) and HNSW index (lines 53-55): `USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 100)`. `JdbcChunkRepository.saveChunk()` uses `PGvector` type (line 50). |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/.../core/ingestion/MarkdownParser.java` | YAML frontmatter parser | VERIFIED (98 lines) | Uses CommonMark with YamlFrontMatterExtension, extracts title/category/tags |
| `src/main/java/.../core/ingestion/HierarchicalChunker.java` | Two-pass chunker | VERIFIED (100 lines) | Parent 1000 tokens, child 200 tokens, 10% overlap |
| `src/main/java/.../infra/embedding/LangChain4jEmbeddingGenerator.java` | ONNX embedding generator | VERIFIED (69 lines) | Uses AllMiniLmL6V2EmbeddingModel, 384 dimensions |
| `src/main/java/.../infra/persistence/JdbcChunkRepository.java` | Chunk persistence with pgvector | VERIFIED (85 lines) | Uses PGvector type for embedding storage |
| `src/main/java/.../infra/persistence/JdbcDocumentRepository.java` | Document persistence | VERIFIED (138 lines) | Handles TEXT[] tags, JSONB frontmatter |
| `src/main/java/.../core/ingestion/IngestionService.java` | Pipeline orchestration | VERIFIED (233 lines) | Full parse-chunk-embed-store pipeline with content hash |
| `src/main/java/.../core/model/Document.java` | Domain model | VERIFIED (55 lines) | Immutable record with tags, frontmatter |
| `src/main/java/.../core/model/Chunk.java` | Domain model | VERIFIED (46 lines) | Parent-child hierarchy support |
| `src/main/resources/db/changelog/changes/002-schema.sql` | Schema with HNSW index | VERIFIED (83 lines) | vector(384), HNSW index with m=16, ef_construction=100 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| IngestionService | MarkdownParser | MarkdownParserPort interface | WIRED | Injected via constructor, called in `ingestFile()` line 129 |
| IngestionService | HierarchicalChunker | ChunkerPort interface | WIRED | Injected via constructor, called in `ingestFile()` line 148 |
| IngestionService | EmbeddingGenerator | EmbeddingGenerator interface | WIRED | Injected via constructor, called for parent (159) and child (173) |
| IngestionService | ChunkRepository | ChunkRepository interface | WIRED | Injected via constructor, `saveChunk()` called for all chunks |
| IngestionService | DocumentRepository | DocumentRepository interface | WIRED | Injected via constructor, save/findByPath/delete called |
| JdbcChunkRepository | pgvector | PGvector Java type | WIRED | `new PGvector(embedding)` used in `saveChunk()` line 50 |
| LangChain4jEmbeddingGenerator | ONNX model | AllMiniLmL6V2EmbeddingModel | WIRED | Instantiated in constructor line 32 |
| All components | Spring DI | @Component/@Service/@Repository | WIRED | All classes annotated for Spring injection |

### Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| ING-01: Parser les fichiers markdown depuis un repertoire | SATISFIED | `IngestionService.ingestDirectory()` + `ingestFile()` |
| ING-02: Chunking hierarchique - parent (1000 tokens), child (200 tokens) | SATISFIED | `HierarchicalChunker` with exact token config |
| ING-03: Generer embeddings avec all-MiniLM-L6-v2 (ONNX local) | SATISFIED | `LangChain4jEmbeddingGenerator` + `AllMiniLmL6V2EmbeddingModel` |
| ING-04: Stocker embeddings dans PostgreSQL/pgvector avec index HNSW | SATISFIED | Schema has vector(384) + HNSW index, JdbcChunkRepository uses PGvector |
| ING-05: Extraire metadonnees depuis frontmatter YAML (titre, tags, categorie) | SATISFIED | `MarkdownParser.extractMetadata()` with CommonMark YAML extension |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| MarkdownParser.java | 68 | `return null` | INFO | Legitimate: returns null when frontmatter field absent |

No blocking anti-patterns found. The `return null` is appropriate for optional fields.

### Test Coverage

| Test File | Lines | Status | Coverage |
|-----------|-------|--------|----------|
| MarkdownParserTest.java | 248 | VERIFIED | Frontmatter extraction edge cases |
| HierarchicalChunkerTest.java | 335 | VERIFIED | Two-pass chunking, overlap behavior |
| IngestionServiceTest.java | 378 | VERIFIED | Full pipeline orchestration, 8 unit tests |
| IngestionIT.java | 261 | VERIFIED | Integration tests with Testcontainers |

### Human Verification Required

#### 1. Full Pipeline Integration

**Test:** Run integration tests with Docker: `./mvnw test -Dgroups=integration`
**Expected:** All 4 IngestionIT tests pass (ingest file, skip unchanged, delete on change, ingest directory)
**Why human:** Requires Docker runtime for PostgreSQL with pgvector

#### 2. Embedding Quality

**Test:** Ingest a real markdown file and verify embedding dimension
**Expected:** Each chunk has a 384-dimensional float array stored in pgvector
**Why human:** Requires database inspection

#### 3. HNSW Index Verification

**Test:** Connect to database and run `\di chunks*` to list indexes
**Expected:** `idx_chunks_embedding` index of type `hnsw` exists
**Why human:** Requires database connection

---

*Verified: 2026-01-20T11:35:00Z*
*Verifier: Claude (gsd-verifier)*
