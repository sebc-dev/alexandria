# Stack RAG avec Infinity, Qwen3 et pgvector : guide technique complet

Les embeddings **Qwen3-Embedding-0.6B produisent des vecteurs de 1024 dimensions**, supportent jusqu'à **32K tokens** en entrée, et nécessitent des **préfixes d'instruction** pour les requêtes (mais pas pour les documents). Combiné à **bge-reranker-v2-m3** en two-stage retrieval et **pgvector avec index HNSW**, ce stack offre un excellent ratio performance/coût pour les applications RAG multilingues.

---

## Qwen3-Embedding-0.6B : spécifications exactes

Le modèle Qwen3-Embedding-0.6B, basé sur Qwen3-0.6B-Base, produit des embeddings de **1024 dimensions par défaut**. Grâce au Matryoshka Representation Learning (MRL), cette dimension peut être réduite à 32, 64, 128, 256 ou 512 selon les besoins de stockage, avec une dégradation progressive de la qualité.

| Spécification | Valeur |
|---------------|--------|
| Dimension des vecteurs | **1024** (défaut), flexible 32-1024 via MRL |
| Context length maximum | **32 768 tokens** |
| Normalisation L2 | Oui, à appliquer post-inférence |
| Distance recommandée | Cosine similarity ou dot product (sur vecteurs normalisés) |
| Langues supportées | 100+ dont français et anglais |

### Préfixes d'instruction obligatoires

Contrairement aux modèles basiques, Qwen3-Embedding **requiert des préfixes d'instruction** pour les requêtes, similaires aux modèles E5-instruct mais avec un format spécifique. Les documents s'encodent directement sans préfixe.

**Format pour les requêtes :**
```
Instruct: {task_description}
Query:{query_text}
```

**Exemple concret :**
```typescript
function formatQuery(task: string, query: string): string {
  return `Instruct: ${task}\nQuery:${query}`;
}

// Usage
const formattedQuery = formatQuery(
  "Given a web search query, retrieve relevant passages that answer the query",
  "Quelles sont les causes du réchauffement climatique?"
);
```

La documentation Qwen précise que **ne pas utiliser d'instruction** entraîne une baisse de **1 à 5%** des performances de retrieval. Pour le contenu multilingue, les instructions doivent être rédigées **en anglais** même si les requêtes et documents sont en français.

### Fonction de distance avec pgvector

Puisque les embeddings sont **L2-normalisés** après génération, cosine similarity et dot product sont mathématiquement équivalents. Avec pgvector, utilisez :
- **`<=>`** (cosine distance) avec `vector_cosine_ops` — choix le plus courant
- **`<#>`** (negative inner product) avec `vector_ip_ops` — légèrement plus rapide sur vecteurs normalisés

---

## bge-reranker-v2-m3 et stratégie two-stage retrieval

Le reranker **bge-reranker-v2-m3** de BAAI est optimisé pour le cross-encoder reranking multilingue. Il évalue la pertinence de paires query-document avec une précision supérieure aux embeddings seuls, au prix d'une latence accrue.

### Format d'entrée et limites techniques

| Paramètre | Valeur |
|-----------|--------|
| Format d'entrée | Paires `[[query, doc1], [query, doc2], ...]` |
| Max tokens (architecture) | 8 192 tokens |
| Max tokens (recommandé) | **1 024 tokens** (configuration d'entraînement) |
| Score brut | Unbounded (typiquement -10 à +10) |
| Score normalisé | 0 à 1 via sigmoid (`normalize=True`) |

**Important** : BAAI recommande explicitement `max_length=1024` malgré le support architectural de 8 192 tokens, car le modèle a été fine-tuné avec cette limite.

### Configuration two-stage retrieval optimale

Le ratio **N:K** (candidats initiaux : résultats finaux) est crucial pour équilibrer recall et latence.

| Use case | N (initial) | K (final) | Ratio |
|----------|-------------|-----------|-------|
| RAG standard | 100 | 5 | 20:1 |
| Q&A simple | 50 | 3 | ~17:1 |
| Recherche exhaustive | 150 | 10 | 15:1 |
| Latency-sensitive | 25 | 3 | ~8:1 |

Les benchmarks BAAI utilisent systématiquement **top-100** pour l'évaluation. Une configuration **N=100, K=5** constitue un excellent point de départ.

### Interprétation des scores et seuillage

Les scores de reranking sont **relatifs, pas absolus** — l'ordre compte plus que la valeur brute. Pour appliquer un seuil :

1. Utiliser `normalize=True` pour obtenir des scores 0-1 (sigmoid appliqué)
2. Calibrer sur **30-50 requêtes représentatives** de votre domaine
3. Le seuil de **0.5 normalisé** est un point de départ, mais doit être ajusté empiriquement

**Quand le reranking apporte de la valeur :**
- Requêtes ambiguës où le contexte détermine l'intention
- Domaines spécialisés avec terminologie technique
- Cas où la précision prime sur la latence
- First-stage retrieval avec recall insuffisant

Le reranking ajoute **50-300ms** de latence selon le nombre de candidats et le hardware. L'analyse coût-bénéfice de NVIDIA montre que le reranking permet d'envoyer **moins de chunks mais de meilleure qualité** au LLM, réduisant les coûts globaux.

---

## Configuration pgvector optimale pour vecteurs 1024D

Pour des embeddings de 1024 dimensions, **HNSW surpasse IVFFlat** en performance de requête, avec l'avantage de ne pas nécessiter de données préexistantes pour créer l'index.

### Index HNSW : paramètres recommandés

| Paramètre | Défaut | Recommandé (1024D) | Impact |
|-----------|--------|-------------------|--------|
| m | 16 | **24-32** | Recall (↑ = meilleur recall, plus de mémoire) |
| ef_construction | 64 | **128-256** | Qualité de l'index (↑ = meilleur, build plus long) |
| ef_search | 40 | **100-200** | Recall à la requête (↑ = meilleur recall, latence ↑) |

```sql
-- Création d'index HNSW optimisé pour Qwen3 1024D
SET maintenance_work_mem = '4GB';
SET max_parallel_maintenance_workers = 7;

CREATE INDEX documents_embedding_idx ON documents 
USING hnsw (embedding halfvec_cosine_ops) 
WITH (m = 24, ef_construction = 128);

-- Configuration de recherche
SET hnsw.ef_search = 100;  -- 100 pour balanced, 200+ pour high recall
```

### halfvec vs vector : économie de stockage substantielle

L'utilisation de **halfvec (16-bit)** au lieu de vector (32-bit) réduit le stockage de **~50%** avec un impact minimal sur le recall (~98% de précision maintenue).

| Type | Stockage (1024D) | Dimensions max indexables |
|------|------------------|---------------------------|
| `vector` | 4 104 bytes | 2 000 |
| `halfvec` | 2 056 bytes | 4 000 |

**Recommandation forte** : utilisez `halfvec(1024)` pour les embeddings Qwen3. Les tests AWS Aurora montrent une réduction de stockage d'index de **66%** sans impact sur les performances de requête.

```sql
CREATE TABLE documents (
    id bigserial PRIMARY KEY,
    content text,
    embedding halfvec(1024),
    metadata jsonb
);
```

### Scaling selon le volume de documents

| Volume | Configuration | Paramètres |
|--------|--------------|------------|
| <10K docs | Index optionnel | m=16, ef_construction=64 |
| 10K-100K | HNSW standard | m=24, ef_construction=128, ef_search=100 |
| 100K-1M | HNSW optimisé | m=32, ef_construction=256, ef_search=200 |
| >1M docs | HNSW + partitioning | Augmenter maintenance_work_mem à 8GB+ |

Pour les requêtes filtrées avec pgvector 0.8.0+, activez le **iterative scanning** :
```sql
SET hnsw.iterative_scan = 'relaxed_order';
SET hnsw.max_scan_tuples = 20000;
```

---

## Chunking optimisé pour Qwen3-Embedding

Malgré le support de **32K tokens**, des chunks de **512 tokens avec 15% d'overlap** offrent le meilleur équilibre précision/contexte. Les chunks trop grands créent des embeddings "dilués" perdant en spécificité topique.

### Tailles recommandées par use case

| Use case | Taille chunk | Overlap | Stratégie |
|----------|-------------|---------|-----------|
| Q&A général | 512 tokens | 15% (~75 tokens) | RecursiveCharacterTextSplitter |
| Documentation technique | 768 tokens | 10% | Structure-based + Recursive |
| Documents juridiques/recherche | 1024 tokens | 15% | Hierarchical [2048, 512, 128] |
| FAQ/Support | 256 tokens | 10% | Sentence-based |
| Contenu multilingue FR+EN | 512 tokens | 15% | Recursive avec séparateurs adaptés |

### RecursiveCharacterTextSplitter comme défaut

Cette stratégie divise hiérarchiquement le texte en respectant la structure sémantique :

```typescript
// Configuration TypeScript/Bun recommandée
const SEPARATORS = ["\n\n", "\n", ". ", " ", ""];

function chunkText(text: string, chunkSize = 512, overlap = 75): string[] {
  // Implémentation recursive splitting
  // Priorité: paragraphes > lignes > phrases > mots
}
```

### Capacités multilingues français-anglais

Qwen3-Embedding gère **excellemment** le français et l'anglais simultanément, avec support du cross-lingual retrieval (requête en français récupérant des documents anglais et vice versa). Le modèle est classé **#1 sur MTEB multilingual** avec un score de 70.58.

**Bonnes pratiques multilingues :**
- Écrire les instructions d'embedding en **anglais** même pour du contenu français
- Maintenir la **cohérence linguistique** au sein de chaque chunk
- Ajouter la langue comme **métadonnée** pour filtrage optionnel

---

## Intégration Infinity + TypeScript/Bun

Infinity expose une **API OpenAI-compatible**, permettant l'utilisation directe du SDK OpenAI avec Bun.

### Client TypeScript recommandé

```typescript
import OpenAI from 'openai';

interface InfinityConfig {
  baseUrl: string;
  apiKey?: string;
  timeoutMs?: number;
  maxRetries?: number;
}

class InfinityClient {
  private client: OpenAI;
  
  constructor(config: InfinityConfig) {
    this.client = new OpenAI({
      apiKey: config.apiKey || 'dummy',
      baseURL: config.baseUrl,
      timeout: config.timeoutMs || 60_000,
      maxRetries: config.maxRetries || 3,
    });
  }
  
  async embed(texts: string[], model: string): Promise<number[][]> {
    const response = await this.client.embeddings.create({
      model,
      input: texts,
    });
    return response.data.map(d => d.embedding);
  }
  
  // Reranking via endpoint natif (hors SDK OpenAI)
  async rerank(query: string, docs: string[], model: string): Promise<number[]> {
    const response = await fetch(
      `${this.client.baseURL?.replace('/v1', '')}/rerank`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.client.apiKey}`,
        },
        body: JSON.stringify({ model, query, docs, return_docs: false }),
      }
    );
    const data = await response.json();
    return data.scores;
  }
}

// Configuration RunPod Serverless
const client = new InfinityClient({
  baseUrl: `https://api.runpod.ai/v2/${process.env.ENDPOINT_ID}/openai/v1`,
  apiKey: process.env.RUNPOD_API_KEY,
  timeoutMs: 120_000,  // Cold start consideration
  maxRetries: 3,
});
```

### Endpoints Infinity disponibles

| Endpoint | Méthode | Usage |
|----------|---------|-------|
| `/v1/embeddings` | POST | Création d'embeddings (OpenAI-compatible) |
| `/v1/models` | GET | Liste des modèles chargés |
| `/rerank` | POST | Reranking de documents |
| `/health` | GET | Health check |

### Configuration optimale

| Paramètre | Valeur recommandée | Notes |
|-----------|-------------------|-------|
| Batch size (client) | **32 texts** | Infinity gère le dynamic batching côté serveur |
| Timeout (warm) | 30-60 secondes | Inférence standard |
| Timeout (cold start) | **120-300 secondes** | Chargement du modèle sur RunPod |
| Retries | 3 avec exponential backoff | 500ms initial, multiplicateur 2x, max 30s |

Pour minimiser les cold starts sur RunPod :
- Activer **FlashBoot** (réduit à 0.5-2s pour les images populaires)
- Configurer **Active Workers ≥ 1** pour éliminer les cold starts (billing always-on)

---

## Configuration complète recommandée

### SQL pgvector

```sql
-- Extension et table
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id bigserial PRIMARY KEY,
    content text NOT NULL,
    embedding halfvec(1024),
    metadata jsonb DEFAULT '{}',
    created_at timestamptz DEFAULT now()
);

-- Index HNSW optimisé
SET maintenance_work_mem = '4GB';
CREATE INDEX documents_embedding_idx ON documents 
USING hnsw (embedding halfvec_cosine_ops) 
WITH (m = 24, ef_construction = 128);

-- Requête type avec reranking candidats
SET hnsw.ef_search = 100;
SELECT id, content, embedding <=> $1::halfvec(1024) AS distance
FROM documents
ORDER BY embedding <=> $1::halfvec(1024)
LIMIT 100;  -- N candidats pour reranking
```

### Pipeline RAG TypeScript complet

```typescript
async function ragPipeline(userQuery: string): Promise<string[]> {
  // 1. Formater la requête avec instruction
  const formattedQuery = `Instruct: Given a user question, retrieve relevant passages\nQuery:${userQuery}`;
  
  // 2. Générer embedding de la requête
  const [queryEmbedding] = await client.embed([formattedQuery], 'Qwen/Qwen3-Embedding-0.6B');
  
  // 3. First-stage: récupérer N=100 candidats depuis pgvector
  const candidates = await db.query(`
    SELECT id, content FROM documents
    ORDER BY embedding <=> $1::halfvec(1024)
    LIMIT 100
  `, [queryEmbedding]);
  
  // 4. Second-stage: reranking avec bge-reranker-v2-m3
  const scores = await client.rerank(
    userQuery,
    candidates.rows.map(r => r.content),
    'BAAI/bge-reranker-v2-m3'
  );
  
  // 5. Retourner top K=5 après reranking
  const rankedResults = candidates.rows
    .map((row, i) => ({ ...row, score: scores[i] }))
    .sort((a, b) => b.score - a.score)
    .slice(0, 5);
  
  return rankedResults.map(r => r.content);
}
```

---

## Conclusion et recommandations clés

Ce stack RAG offre un équilibre optimal entre performance, coût et simplicité d'intégration. **Qwen3-Embedding-0.6B** fournit des embeddings de haute qualité avec support multilingue natif, tandis que **bge-reranker-v2-m3** améliore significativement la précision du retrieval.

Les points d'attention principaux sont l'utilisation systématique des **préfixes d'instruction** pour les requêtes (gain de 1-5%), le choix de **halfvec** pour réduire le stockage de 50%, et la configuration du two-stage retrieval avec un ratio **N:K de 20:1** (100 candidats, 5 résultats finaux).

Pour les déploiements RunPod serverless, prévoyez des **timeouts de 120-300 secondes** pour gérer les cold starts, ou activez FlashBoot/Active Workers pour les environnements de production nécessitant une latence prévisible.