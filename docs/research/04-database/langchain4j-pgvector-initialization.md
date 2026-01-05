# Langchain4j + pgvector : guide complet d'initialisation du schéma PostgreSQL

**Langchain4j ne crée pas automatiquement l'index HNSW** — seul un index IVFFlat est disponible via l'option `useIndex=true`. Pour votre projet RAG avec BGE-M3 (1024 dimensions), vous devrez créer l'extension pgvector et l'index HNSW manuellement, ou via Flyway. Ce guide détaille exactement ce que fait `createTable=true` et propose une stratégie d'initialisation optimale.

---

## Ce que crée exactement createTable=true

L'option `createTable=true` de `PgVectorEmbeddingStore` génère une table avec **quatre colonnes** mais ne touche ni à l'extension ni aux index HNSW. Le SQL exécuté ressemble à ceci :

```sql
CREATE TABLE IF NOT EXISTS my_embeddings (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(1024) NOT NULL,
    text TEXT,
    metadata JSONB NULL
);
```

| Colonne | Type PostgreSQL | Description |
|---------|-----------------|-------------|
| `embedding_id` | UUID | Clé primaire auto-générée |
| `embedding` | vector(1024) | Vecteur d'embedding (dimension configurable) |
| `text` | TEXT | Contenu textuel du segment |
| `metadata` | JSON ou JSONB | Métadonnées selon `metadataStorageConfig` |

Le paramètre `metadataStorageConfig` contrôle le format des métadonnées : `combinedJson()` (défaut), `combinedJsonb()` (recommandé pour les requêtes), ou colonnes individuelles via `columnPerKey()`.

**Points critiques non gérés automatiquement :**
- L'extension `vector` n'est **jamais créée** par Langchain4j standard
- Aucun index HNSW n'est créé — uniquement **IVFFlat** si `useIndex=true`
- Les privilèges PostgreSQL doivent être configurés au préalable

---

## L'extension pgvector doit être créée manuellement

Contrairement à ce qu'on pourrait attendre, **Langchain4j ne crée pas l'extension pgvector**. L'application échouera au démarrage si l'extension n'existe pas déjà. L'exception Quarkus-langchain4j avec `registerVectorPgExtension=true` en mode dev ne s'applique pas au module standard.

### Prérequis et privilèges

```sql
-- Vérifier si l'utilisateur est superuser (requis pour CREATE EXTENSION)
SELECT current_user, usesuper FROM pg_user WHERE usename = current_user;

-- Créer l'extension (exécuter en tant que superuser)
CREATE EXTENSION IF NOT EXISTS vector;

-- Vérifier la version installée
SELECT extversion FROM pg_extension WHERE extname = 'vector';
-- Attendu: 0.8.1
```

| Privilège | Nécessaire pour |
|-----------|-----------------|
| **SUPERUSER** | Créer l'extension pgvector (utilise des fonctions C) |
| CREATE sur schéma | Créer tables et index |
| USAGE sur schéma | Accéder aux objets |

**Solution recommandée** : utilisez l'image Docker `pgvector/pgvector:pg16` ou `pg17` qui inclut l'extension pré-installée. Il suffit ensuite d'exécuter `CREATE EXTENSION IF NOT EXISTS vector;` sans avoir besoin de privilèges superuser pour l'installation.

---

## Index HNSW : création manuelle obligatoire

**Langchain4j ne supporte que IVFFlat**, pas HNSW. L'option `useIndex=true` crée uniquement :

```sql
-- Ce que fait Langchain4j avec useIndex=true + indexListSize=100
CREATE INDEX ON my_embeddings 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);
```

Pour vos besoins avec **1024 dimensions et similarité cosinus**, un index HNSW est nettement supérieur. Voici le script optimal :

```sql
-- Configuration pour accélérer la création (ajuster selon RAM disponible)
SET maintenance_work_mem = '4GB';
SET max_parallel_maintenance_workers = 7;

-- Index HNSW optimisé pour vos paramètres
CREATE INDEX CONCURRENTLY idx_embeddings_hnsw
ON my_embeddings USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 128);
```

### Paramètres HNSW expliqués

| Paramètre | Votre config | Plage | Impact |
|-----------|--------------|-------|--------|
| **m** | 16 | 5-48 | Connexions par nœud — ↑ qualité, ↑ mémoire |
| **ef_construction** | 128 | ≥ 2×m | Qualité du graphe à la construction |
| **ef_search** | 100 (runtime) | 40-400 | Qualité de recherche — configurable par session |

Pour 1024 dimensions, vos valeurs **m=16, ef_construction=128** sont un bon équilibre. Pour des datasets volumineux (>1M vecteurs), augmentez à **m=24-32**.

### Configuration de recherche au runtime

```sql
-- Par session (recommandé)
SET hnsw.ef_search = 100;

-- Par transaction (contrôle fin)
BEGIN;
SET LOCAL hnsw.ef_search = 200;
SELECT id, 1 - (embedding <=> '[...]') AS similarity
FROM my_embeddings
ORDER BY embedding <=> '[votre_vecteur]'
LIMIT 10;
COMMIT;
```

L'operator `<=>` correspond à `vector_cosine_ops` pour la distance cosinus. Pour obtenir la **similarité** (0-1), utilisez `1 - (embedding <=> query_vector)`.

---

## Flyway vs createTable=true : quelle stratégie choisir

Pour un projet mono-utilisateur simple, **createTable=true peut suffire** pour le prototypage, mais Flyway offre des avantages significatifs même pour les petits projets.

### Comparaison des approches

| Critère | createTable=true | Flyway |
|---------|------------------|--------|
| Extension pgvector | ❌ Non géré | ✅ Script V1 |
| Index HNSW | ❌ Non supporté | ✅ Script V3 |
| Versioning schéma | ❌ Aucun | ✅ Table flyway_schema_history |
| Migrations futures | ❌ Complexe | ✅ Nouveaux scripts Vn |
| Reproductibilité | ❌ Aléatoire | ✅ Identique partout |
| Effort initial | Nul | ~15 minutes |

### Recommandation par contexte

- **POC/démo jetable** → `createTable=true` + scripts SQL manuels pour extension/index
- **Projet avec données importantes** → Flyway (même mono-utilisateur)
- **Production** → Flyway **obligatoire**

Le vrai argument pour Flyway même sur un projet simple : vous devez de toute façon créer l'extension et l'index HNSW manuellement. Autant versionner ces scripts.

---

## Scripts Flyway recommandés

Créez la structure `src/main/resources/db/migration/` avec ces fichiers :

### V1__create_pgvector_extension.sql

```sql
-- Activer l'extension pgvector
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

### V2__create_embeddings_table.sql

```sql
-- Table pour Langchain4j PgVectorEmbeddingStore
-- Note: Compatible avec createTable=false dans la config Java
CREATE TABLE IF NOT EXISTS embeddings (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(1024) NOT NULL,  -- BGE-M3: 1024 dimensions
    text TEXT,
    metadata JSONB DEFAULT '{}'
);

COMMENT ON TABLE embeddings IS 'Stockage des embeddings pour RAG - Langchain4j';
COMMENT ON COLUMN embeddings.embedding IS 'Vecteur BGE-M3 (1024 dimensions)';
```

### V3__create_hnsw_index.sql

```sql
-- Index HNSW pour recherche par similarité cosinus
-- À exécuter APRÈS le chargement initial des données pour de meilleures performances
CREATE INDEX IF NOT EXISTS idx_embeddings_hnsw
ON embeddings USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 128);

-- Index GIN sur métadonnées pour filtrage rapide
CREATE INDEX IF NOT EXISTS idx_embeddings_metadata
ON embeddings USING gin (metadata);
```

### Configuration Spring Boot complète

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ragdb
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    clean-disabled: true  # CRITIQUE: empêche suppression accidentelle
  
  jpa:
    hibernate:
      ddl-auto: validate  # Valide cohérence sans modifier le schéma
```

### Configuration Java PgVectorEmbeddingStore

```java
@Bean
public PgVectorEmbeddingStore embeddingStore(DataSource dataSource) {
    return PgVectorEmbeddingStore.builder()
        .datasource(dataSource)
        .table("embeddings")
        .dimension(1024)  // BGE-M3
        .createTable(false)  // Flyway gère le schéma
        .useIndex(false)     // Index HNSW créé par Flyway
        .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
        .build();
}
```

---

## Procédure de premier démarrage

### Option A : Avec Flyway (recommandé)

L'ordre est automatique — Flyway exécute les scripts V1, V2, V3 séquentiellement au démarrage :

1. **Spring Boot démarre** → Flyway se connecte
2. **V1** : Extension vector créée
3. **V2** : Table embeddings créée
4. **V3** : Index HNSW créé
5. **Hibernate valide** le schéma
6. **PgVectorEmbeddingStore** se connecte (table prête)
7. Application opérationnelle

### Option B : Scripts manuels + createTable=true

```bash
# 1. Connexion PostgreSQL en superuser
psql -U postgres -d ragdb

# 2. Créer l'extension
CREATE EXTENSION IF NOT EXISTS vector;

# 3. Démarrer l'application (createTable=true crée la table)
./mvnw spring-boot:run

# 4. Après démarrage, créer l'index HNSW manuellement
psql -U postgres -d ragdb -c "
SET maintenance_work_mem = '2GB';
CREATE INDEX CONCURRENTLY idx_embeddings_hnsw
ON embeddings USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 128);
"
```

### Vérification post-démarrage

```sql
-- Vérifier l'extension
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

-- Vérifier la table
\d embeddings

-- Vérifier les index
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'embeddings';

-- Tester que l'index HNSW est utilisé
EXPLAIN ANALYZE 
SELECT * FROM embeddings 
ORDER BY embedding <=> '[0.1, 0.2, ...]'  -- vecteur test 1024 dims
LIMIT 10;
-- Doit afficher "Index Scan using idx_embeddings_hnsw"
```

---

## Conclusion et recommandation finale

Pour votre projet RAG Java avec **Langchain4j 1.0.0, PostgreSQL 18.1, pgvector 0.8.1**, la stratégie optimale dépend de votre contexte :

**Projet mono-utilisateur simple mais avec données importantes** : utilisez Flyway avec les trois scripts fournis. L'investissement initial de 15 minutes vous garantit :
- L'extension pgvector correctement initialisée
- Un index **HNSW** (pas IVFFlat) avec vos paramètres exacts
- La capacité d'évoluer le schéma proprement

**Configuration recommandée** : `createTable=false` + `useIndex=false` dans Langchain4j, laissez Flyway gérer entièrement le schéma. Cette séparation des responsabilités évite les surprises et facilite le débogage.

Le point critique à retenir : **Langchain4j ne crée jamais d'index HNSW** — c'est IVFFlat ou rien. Pour des performances optimales sur vos vecteurs 1024 dimensions avec recherche cosinus, l'index HNSW manuel est indispensable.