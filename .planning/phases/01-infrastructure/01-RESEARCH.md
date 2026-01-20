# Phase 1 Research: Infrastructure

## Summary

PostgreSQL 17 avec pgvector 0.8.1 et Apache AGE 1.6.0 sont pleinement compatibles. Aucune image Docker pre-built ne combine les deux extensions - un Dockerfile custom basé sur `pgvector/pgvector:pg17` avec compilation AGE est requis. Le driver JDBC AGE n'est pas sur Maven Central et doit être compilé manuellement.

**Validation status:** 26/28 claims verified accurate (2 with nuance)

## Key Findings

### Docker Setup

**Image de base:** `pgvector/pgvector:0.8.1-pg17` (maps to 0.8.1-pg17-bookworm)

**Apache AGE:** Doit être compilé depuis le tag `PG17/v1.6.0-rc0` (publié 22 sept 2025, latest pour PG17)

**Configuration critique:**
- `shared_preload_libraries = 'age'` dans postgresql.conf (pgvector n'en a PAS besoin - juste CREATE EXTENSION)
- `shm_size: 1g` minimum dans docker-compose (doit être >= maintenance_work_mem)
- `maintenance_work_mem`:
  - **512MB - 1GB** pour petits datasets (jusqu'à quelques millions de vecteurs)
  - **2GB - 8GB** recommandé pour datasets plus grands (production)

**Volumes:**
- `/var/lib/postgresql/data` pour persistance
- `/docker-entrypoint-initdb.d` pour scripts init

**Note Windows:** PostgreSQL 17.0-17.2 peuvent avoir des erreurs de linking - upgrader vers **17.3+**

### Database Schema

**Choix HNSW vs IVFFlat:** HNSW recommandé
- 15.5x plus rapide (benchmarks Tembo: 40.5 QPS vs 2.6 QPS à 0.998 recall)
- Pas de rebuild après updates (IVFFlat: centroids non recalculés = dégradation recall)
- Support natif UPDATE/DELETE contrairement à beaucoup d'implémentations HNSW

**Structure tables:**
- `documents`: path, title, category, tags[], content_hash (SHA256), frontmatter JSONB
- `chunks`: hiérarchie parent/child via FK auto-référentielle (pas AGE), chunk_type enum
- `chunk_embeddings`: table séparée pour versioning modèle, vector(384)

**Index HNSW:**
- `m=16` (défaut pgvector, optimal pour dimensions moyennes)
- `ef_construction=64` (défaut) ou `100` (meilleur recall, build plus long)
- `ef_search=40` (défaut) à `100` pour >95% recall

**Full-text:** Configuration `'simple'` pour contenu mixte FR/EN
- **Avantage:** Préserve termes techniques, code, identifiants sans transformation
- **Trade-off:** Pas de stemming ("searching" ne matchera pas "search")
- Alternative si recherche langage naturel requise: configs langue-spécifiques ou PGroonga

**Apache AGE usage:** Réservé aux relations complexes (SIMILAR_TO, REFERENCES entre docs), pas pour parent/child

### Maven Dependencies

**LangChain4j BOM:** Version 1.10.0 (définit `langchain4j.stable.version` et `langchain4j.beta.version`)

**Module embeddings:** `langchain4j-embeddings-all-minilm-l6-v2`
- Utilise la version **beta** (1.10.0-beta18) via le BOM
- Bundle ONNX Runtime (~90MB JAR)
- Variante quantifiée disponible (suffixe `-q`)

**all-MiniLM-L6-v2 specs:**
- **384 dimensions** (confirmé Hugging Face model card)
- 22.7M paramètres
- Max sequence length: 256 tokens

**pgvector Java:** `com.pgvector:pgvector:0.1.6` (publié July 2024, 256 dependents)

**Apache AGE JDBC:** Non disponible sur Maven Central
- Feature request ouverte sur AGE mailing list
- Compilation requise depuis `age/drivers/jdbc` avec Gradle

**Java version:** 21 (LTS release, virtual threads GA)

### Java 21 Features for RAG Project

**Virtual Threads (JEP 444 - GA):**
- Pas de `--enable-preview` requis
- Idéal pour I/O-bound: connexions DB, embedding generation, file parsing
- `Executors.newVirtualThreadPerTaskExecutor()` pour parallélisation massive
- HikariCP compatible mais configurer `maximumPoolSize` plus bas (virtual threads moins chers)

**Sequenced Collections (JEP 431 - GA):**
- `SequencedCollection`, `SequencedSet`, `SequencedMap`
- `collection.getFirst()`, `collection.getLast()`, `collection.reversed()`
- Utile pour chunks ordonnés, résultats de recherche

**Record Patterns (JEP 440 - GA):**
```java
record SearchResult(String content, double score, Metadata meta) {}
if (result instanceof SearchResult(var content, var score, _)) {
    // destructuring direct
}
```

**Pattern Matching for switch (JEP 441 - GA):**
```java
return switch (chunk) {
    case ParentChunk p -> processParent(p);
    case ChildChunk c -> processChild(c);
    case null -> handleNull();
};
```

**String Templates (JEP 430 - Preview):**
- Pas recommandé pour v1 (preview, peut changer)
- Alternative: `String.format()` ou `MessageFormat`

**Scoped Values (JEP 446 - Preview):**
- Pas recommandé pour v1 (preview)
- Alternative: ThreadLocal pour context propagation

**Recommendations pour le projet:**
1. **Virtual Threads:** Activer pour document ingestion parallèle et batch embedding
2. **Records:** Utiliser pour DTOs (SearchResult, ChunkMetadata, DocumentInfo)
3. **Pattern Matching:** Simplifier handling des types de chunks
4. **Sequenced Collections:** Pour résultats ordonnés par score

### Validation Tests

**pgvector:**
- `CREATE EXTENSION vector;` (pas de LOAD nécessaire, pas de shared_preload_libraries)
- Format insertion: `'[1.0, 2.0, ...]'::vector` (cast optionnel si colonne déjà vector)
- Opérateur `<=>` retourne **distance cosine** (0 = identique, 2 = opposé)
  - Pour similarité: `1 - (embedding <=> '[...]')`
- Configurer `ef_search` par session: `SET hnsw.ef_search = 100` (range: 1-1000)

**Apache AGE:**
- `LOAD 'age';` OBLIGATOIRE à chaque connexion (charge la lib dans l'address space)
- `SET search_path = ag_catalog, "$user", public;` recommandé (sinon préfixer avec `ag_catalog.`)
- Format requête: `SELECT * FROM cypher('graph', $$ QUERY $$) AS (col agtype)`
- Clause AS requise même pour requêtes sans résultats
- Résultat agtype parsable en JSON

**HikariCP config:**
```java
config.setConnectionInitSql(
    "LOAD 'age'; SET search_path = ag_catalog, \"$user\", public"
);
```

## Constraints & Gotchas

1. **AGE session init:** Chaque connexion JDBC doit exécuter LOAD + SET search_path
2. **AGE driver:** Pas sur Maven Central, scope system ou install local requis
3. **maintenance_work_mem:** Si index HNSW dépasse cette valeur, performance 10x+ plus lente (écritures disque temporaires)
4. **AGE 1.6.0-rc0:** Suffixe -rc0 mais version stable, pas de migration depuis versions précédentes
5. **Symlink AGE:** Pour non-superuser: `ln -s age.so /usr/lib/postgresql/17/lib/plugins/age.so` + `GRANT USAGE ON SCHEMA ag_catalog TO db_user;`
6. **Cosine operator:** `<=>` retourne distance, pas similarité - utiliser `1 - distance` pour score

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| HNSW over IVFFlat | 15x faster queries, no rebuild on updates |
| ef_construction=64 (default) | Balance build time/recall, can tune to 100 if needed |
| Embeddings in separate table | Enables model versioning and zero-downtime migration |
| FK for parent/child, AGE for cross-refs | Simpler queries for hierarchy, AGE excels at complex traversals |
| `'simple'` tsvector config | Mixed FR/EN technical content, preserves terms (no stemming trade-off acceptable) |
| Monolithic Maven project | Sufficient for personal tool, can modularize later |
| Java 21 with virtual threads | LTS release, I/O-bound workload benefits from lightweight concurrency |
| Records for DTOs | Immutable, concise, pattern matching compatible |
| No preview features | Stability over bleeding edge (String Templates, Scoped Values excluded) |

## Files to Create

1. `Dockerfile` - PostgreSQL 17 + pgvector + AGE
2. `docker-compose.yml` - Service config with volumes and health check
3. `postgresql.conf` - Memory and extension config
4. `init/001_extensions.sql` - CREATE EXTENSION and graph creation
5. `init/002_schema.sql` - Tables, indexes, functions
6. `pom.xml` - Maven dependencies
7. `lib/age-jdbc-1.6.0.jar` - Compiled AGE driver (via build script)

## Open Questions

None - research is complete and validated.

---
*Research completed: 2026-01-19*
*Validated: 2026-01-19 (26/28 claims accurate, 2 with nuance)*
*Sources: 4 research documents in .planning/research/results/*
