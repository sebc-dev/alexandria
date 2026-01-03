# Déployer des modèles d'embedding et reranking sur RunPod Serverless

Pour votre projet Alexandria (serveur MCP), **RunPod Serverless est une option viable et économique** pour déployer Qwen3-Embedding-0.6B et bge-reranker-v2-m3. Avec ~100-500 requêtes/jour, le coût mensuel estimé se situe entre **$15-40** selon le GPU choisi. Les deux modèles tiennent confortablement sur un GPU 16GB (A4000) ou 24GB (L4), avec une latence d'inférence bien sous les 2 secondes une fois le worker chaud.

---

## 1. Serverless vs Pods : analyse comparative

RunPod propose deux modes de déploiement avec des modèles tarifaires distincts. **Pour 100-500 req/jour, le Serverless est clairement recommandé** car il évite de payer 24/7 pour un GPU sous-utilisé.

### Tarification Serverless (facturation à la seconde)

| GPU | VRAM | Flex Worker (/sec) | Équivalent horaire | Utilisation |
|-----|------|-------------------|-------------------|-------------|
| A4000, A4500, RTX 4000 | 16GB | **$0.00016** | $0.58/h | ✅ Plus économique |
| L4, A5000, 3090 | 24GB | **$0.00019** | $0.69/h | ✅ Recommandé |
| 4090 PRO | 24GB | $0.00031 | $1.10/h | Bonne perf |
| A100 | 80GB | $0.00076 | $2.72/h | Surdimensionné |

**Note importante** : RunPod Serverless ne propose pas de T4. Le tier d'entrée est le GPU 16GB (A4000/A4500).

### Tarification Pods (always-on)

Un Pod L4 coûte environ **$175-250/mois** en Community Cloud. Pour 500 req/jour avec 5 secondes de traitement moyen, vous n'utilisez que ~42 minutes de GPU par jour. Le Serverless coûtera ~**$10-25/mois** — soit **8-10x moins cher**.

### Recommandation

**Utilisez Serverless** avec un GPU 16GB (A4000) ou 24GB (L4). Le Serverless est idéal pour les charges intermittentes. Les cold starts sont acceptables dans votre cas (vous avez mentionné que c'est tolérable).

---

## 2. Structure du handler Python pour inference

Le handler est le cœur de votre worker RunPod. Voici la structure complète pour vos deux modèles.

### handler.py pour Qwen3-Embedding-0.6B

```python
# handler.py - Qwen3 Embedding Worker
import runpod
import torch
from sentence_transformers import SentenceTransformer

# ============================================================
# CHARGEMENT GLOBAL - exécuté une seule fois au démarrage
# ============================================================
print("Chargement de Qwen3-Embedding-0.6B...")
device = "cuda" if torch.cuda.is_available() else "cpu"

model = SentenceTransformer(
    "Qwen/Qwen3-Embedding-0.6B",
    device=device,
    model_kwargs={"torch_dtype": torch.float16},  # FP16 pour réduire VRAM
    tokenizer_kwargs={"padding_side": "left"}      # Requis pour Qwen3
)
print(f"Modèle chargé sur {device}")


def handler(job):
    """
    Input: {"texts": ["texte1", "texte2", ...], "normalize": true}
    Output: {"embeddings": [[0.1, ...], ...], "dimensions": 1024}
    """
    try:
        job_input = job["input"]
        texts = job_input.get("texts", [])
        
        if isinstance(texts, str):
            texts = [texts]
        
        if not texts:
            return {"error": "Champ 'texts' requis"}
        
        normalize = job_input.get("normalize", True)
        batch_size = job_input.get("batch_size", 32)
        
        # Génération des embeddings
        embeddings = model.encode(
            texts,
            batch_size=batch_size,
            normalize_embeddings=normalize,
            convert_to_numpy=True,
            show_progress_bar=False
        )
        
        return {
            "embeddings": embeddings.tolist(),
            "dimensions": embeddings.shape[1],
            "count": len(texts)
        }
        
    except Exception as e:
        return {"error": str(e)}


runpod.serverless.start({"handler": handler})
```

### handler.py pour bge-reranker-v2-m3

```python
# handler.py - BGE Reranker Worker
import runpod
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

# ============================================================
# CHARGEMENT GLOBAL
# ============================================================
print("Chargement de bge-reranker-v2-m3...")
device = "cuda" if torch.cuda.is_available() else "cpu"

model_name = "BAAI/bge-reranker-v2-m3"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForSequenceClassification.from_pretrained(
    model_name,
    torch_dtype=torch.float16,
    device_map=device
)
model.eval()
print(f"Reranker chargé sur {device}")


def handler(job):
    """
    Input: {
        "query": "question utilisateur",
        "documents": ["doc1", "doc2", ...],
        "top_k": 10
    }
    Output: {
        "rankings": [{"index": 0, "score": 0.95, "text": "..."}, ...]
    }
    """
    try:
        job_input = job["input"]
        query = job_input.get("query", "")
        documents = job_input.get("documents", [])
        top_k = job_input.get("top_k", len(documents))
        
        if not query or not documents:
            return {"error": "Champs 'query' et 'documents' requis"}
        
        # Créer les paires query-document
        pairs = [[query, doc] for doc in documents]
        
        # Tokenization
        inputs = tokenizer(
            pairs,
            padding=True,
            truncation=True,
            max_length=512,
            return_tensors="pt"
        ).to(device)
        
        # Inference
        with torch.no_grad():
            scores = model(**inputs).logits.squeeze(-1).float()
        
        # Tri par score décroissant
        scores = scores.cpu().numpy().tolist()
        ranked = sorted(
            enumerate(zip(documents, scores)),
            key=lambda x: x[1][1],
            reverse=True
        )[:top_k]
        
        return {
            "rankings": [
                {"index": idx, "score": score, "text": text[:200]}
                for idx, (text, score) in ranked
            ]
        }
        
    except Exception as e:
        return {"error": str(e)}


runpod.serverless.start({"handler": handler})
```

### Dockerfile optimisé

```dockerfile
FROM runpod/pytorch:2.2.0-py3.10-cuda12.1.1-devel-ubuntu22.04

WORKDIR /app

ENV PYTHONUNBUFFERED=1
ENV HF_HOME=/app/cache
ENV TOKENIZERS_PARALLELISM=false

# Copier et installer les dépendances
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copier le handler
COPY handler.py .

# Optionnel: pré-télécharger le modèle (augmente l'image mais réduit cold start)
# RUN python -c "from sentence_transformers import SentenceTransformer; \
#     SentenceTransformer('Qwen/Qwen3-Embedding-0.6B')"

CMD ["python", "-u", "handler.py"]
```

### requirements.txt

```txt
runpod>=1.6.0
torch>=2.0.0
transformers>=4.40.0
sentence-transformers>=2.6.0
numpy>=1.24.0
```

---

## 3. Architecture : 1 endpoint ou 2 endpoints séparés ?

| Critère | 1 Endpoint (combiné) | 2 Endpoints (séparés) |
|---------|---------------------|----------------------|
| **Cold starts** | 1 seul cold start par requête complète | 2 cold starts si workers différents |
| **VRAM** | ~4-5 GB (les deux modèles) | ~2-2.5 GB chacun |
| **Scaling** | Moins flexible | Scale indépendamment |
| **Maintenance** | 1 image Docker | 2 images distinctes |
| **Latence** | Optimale (pas de network hop) | +100-200ms entre appels |

### Recommandation : 2 endpoints séparés

Pour votre cas d'usage, **2 endpoints séparés sont recommandés** :

1. **Indépendance** : L'embedding est appelé à l'indexation (write), le reranking à la recherche (read)
2. **Scaling différent** : Vous pouvez avoir plus d'embedders si vous indexez beaucoup
3. **Maintenance** : Mise à jour d'un modèle sans toucher l'autre
4. **Cold start acceptable** : Avec FlashBoot et idle timeout, les deux workers restent souvent warm

Le surcoût de latence (~100ms) est négligeable par rapport à votre budget de 2 secondes.

---

## 4. Configuration GPU et VRAM

### Requirements VRAM par modèle

| Modèle | Paramètres | Poids FP16 | VRAM inference | Avec batch=32 |
|--------|-----------|------------|----------------|---------------|
| **Qwen3-Embedding-0.6B** | 600M | 1.19 GB | ~1.5 GB | ~2-3 GB |
| **bge-reranker-v2-m3** | 568M | ~1.1 GB | ~1.5 GB | ~2-2.5 GB |
| **Overhead PyTorch/CUDA** | - | - | ~1.5 GB | ~1.5 GB |

### Faisabilité par GPU

| GPU | VRAM | Embedder seul | Reranker seul | Les deux | Verdict |
|-----|------|---------------|---------------|----------|---------|
| **A4000 (16GB)** | 16 GB | ✅ ~3 GB | ✅ ~3 GB | ⚠️ ~6-7 GB | Suffisant |
| **L4 (24GB)** | 24 GB | ✅ ~3 GB | ✅ ~3 GB | ✅ ~6-7 GB | **Recommandé** |
| **A10 (24GB)** | 24 GB | ✅ | ✅ | ✅ | Plus cher |

### Recommandation GPU

**L4 (24GB) à $0.00019/sec** est le choix optimal :
- VRAM confortable avec 17+ GB de marge
- Architecture Ada Lovelace moderne (meilleure efficacité)
- Coût raisonnable (~$0.69/h)

Le **A4000 (16GB) à $0.00016/sec** fonctionne aussi si vous utilisez 2 endpoints séparés.

---

## 5. API et intégration TypeScript

### Format de requête/réponse

```typescript
// Requête sync
POST https://api.runpod.ai/v2/{endpoint_id}/runsync
Headers: { "authorization": "YOUR_API_KEY", "content-type": "application/json" }
Body: { "input": { "texts": ["texte1", "texte2"] } }

// Réponse
{
  "id": "sync-abc123",
  "status": "COMPLETED",
  "delayTime": 824,        // ms d'attente en queue
  "executionTime": 1200,   // ms d'exécution
  "output": { "embeddings": [[0.1, ...], ...] }
}
```

### Client TypeScript complet pour Bun

```typescript
// runpod-client.ts
const RUNPOD_API_KEY = process.env.RUNPOD_API_KEY!;
const EMBED_ENDPOINT = process.env.EMBED_ENDPOINT_ID!;
const RERANK_ENDPOINT = process.env.RERANK_ENDPOINT_ID!;

interface EmbedInput { texts: string[]; normalize?: boolean }
interface EmbedOutput { embeddings: number[][]; dimensions: number }

interface RerankInput { query: string; documents: string[]; top_k?: number }
interface RerankOutput { rankings: Array<{ index: number; score: number; text: string }> }

async function callRunPod<TIn, TOut>(
  endpointId: string,
  input: TIn,
  timeoutMs = 60000
): Promise<TOut> {
  const response = await fetch(
    `https://api.runpod.ai/v2/${endpointId}/runsync?wait=${timeoutMs}`,
    {
      method: "POST",
      headers: {
        "authorization": RUNPOD_API_KEY,
        "content-type": "application/json",
      },
      body: JSON.stringify({ input }),
    }
  );

  if (!response.ok) {
    throw new Error(`RunPod error: ${response.status} ${await response.text()}`);
  }

  const result = await response.json();
  
  if (result.status === "FAILED") {
    throw new Error(`Job failed: ${result.error}`);
  }
  
  return result.output as TOut;
}

// API publique
export async function generateEmbeddings(texts: string[]): Promise<number[][]> {
  const result = await callRunPod<EmbedInput, EmbedOutput>(
    EMBED_ENDPOINT,
    { texts, normalize: true }
  );
  return result.embeddings;
}

export async function rerankDocuments(
  query: string,
  documents: string[],
  topK = 10
): Promise<RerankOutput["rankings"]> {
  const result = await callRunPod<RerankInput, RerankOutput>(
    RERANK_ENDPOINT,
    { query, documents, top_k: topK }
  );
  return result.rankings;
}

// Flow complet: search avec reranking
export async function semanticSearch(
  query: string,
  candidates: string[],
  topK = 5
): Promise<string[]> {
  const rankings = await rerankDocuments(query, candidates, topK);
  return rankings.map(r => candidates[r.index]);
}
```

### Batching

Le batching se gère **au niveau du handler** (pas de l'API). Envoyez plusieurs textes dans un seul appel :

```typescript
// ✅ Efficace : 1 appel avec 50 textes
const embeddings = await generateEmbeddings(["text1", "text2", ..., "text50"]);

// ❌ Inefficace : 50 appels séparés
for (const text of texts) {
  await generateEmbeddings([text]); // Cold start à chaque fois!
}
```

### Sync vs Async

- **`/runsync`** : Bloque jusqu'à complétion. Timeout max 5 minutes avec `?wait=300000`. **Utilisez pour latence < 30s.**
- **`/run`** : Retourne immédiatement un job ID. Poll `/status/{id}` pour les résultats. **Utilisez pour tâches longues.**

Pour embedding/reranking (~1-2s), utilisez **toujours `/runsync`**.

### SDK officiel

```bash
npm install runpod-sdk
```

```typescript
import runpodSdk from "runpod-sdk";

const runpod = runpodSdk(process.env.RUNPOD_API_KEY);
const endpoint = runpod.endpoint(EMBED_ENDPOINT);

const result = await endpoint.runSync({ 
  input: { texts: ["hello world"] } 
});
```

---

## 6. Cold start et optimisation

### Cold start typiques

| Configuration | Cold start |
|---------------|------------|
| Sans optimisation, modèle téléchargé au runtime | 30-60 secondes |
| Modèle baked dans l'image Docker | **10-20 secondes** |
| Avec FlashBoot (endpoint populaire) | **0.5-5 secondes** |
| Active Worker (toujours warm) | **0 secondes** |

### Configuration min workers (garder warm)

```
Endpoint Settings > Active Workers: 1
```

**Coût d'un worker warm 24/7** :
- A4000 : $0.00011/sec × 86400 = **~$9.50/jour = ~$285/mois**
- L4 : $0.00013/sec × 86400 = **~$11.23/jour = ~$337/mois**

❌ **Non recommandé** pour 100-500 req/jour. Le coût dépasse largement le Serverless pur.

### FlashBoot

FlashBoot est **gratuit et automatique**. Il conserve l'état du worker après scale-down et le "réveille" plus rapidement. Plus votre endpoint a de trafic régulier, plus FlashBoot est efficace.

Résultats typiques :
- Sans FlashBoot : 42 secondes max
- Avec FlashBoot : **563ms minimum**

### Idle timeout recommandé

Configurez un idle timeout de **30-60 secondes** pour garder les workers warm entre les requêtes groupées :

```
Endpoint Settings > Idle Timeout: 30
```

Coût : Si vous avez ~500 req/jour groupées en sessions, l'idle timeout ajoute ~$2-5/mois.

### Network volumes

Les network volumes persistent les poids des modèles à **$0.07/GB/mois**. Pour ~3 GB de modèles : ~$0.21/mois.

**Cependant**, RunPod recommande de **baker les modèles dans l'image Docker** pour les cold starts les plus rapides. Les network volumes sont plus lents car ils nécessitent un accès réseau.

---

## 7. Estimation des coûts mensuels

### Hypothèses

- 500 requêtes/jour × 30 jours = **15,000 requêtes/mois**
- Chaque requête = 1 embed + 1 rerank (50 candidats)
- Temps embedding : ~1 seconde
- Temps reranking : ~2 secondes
- Cold starts : ~30/jour (avec idle timeout 30s)

### Calcul détaillé (2 endpoints L4)

| Composant | Calcul | Coût |
|-----------|--------|------|
| **Embedding (processing)** | 15,000 req × 1s × $0.00019 | $2.85 |
| **Reranking (processing)** | 15,000 req × 2s × $0.00019 | $5.70 |
| **Cold starts** | 900/mois × 15s × $0.00019 | $2.57 |
| **Idle time** | ~25h/mois × $0.69 | $17.25 |
| **Storage (optionnel)** | 3 GB × $0.07 | $0.21 |
| **Total L4** | | **~$28-35/mois** |

### Avec A4000 (16GB) - Option économique

| Composant | Coût |
|-----------|------|
| Processing | $7.20 |
| Cold starts | $2.16 |
| Idle | $14.50 |
| **Total A4000** | **~$24-30/mois** |

### Comparaison avec alternatives

| Plateforme | Coût estimé/mois | Cold start | Complexité |
|------------|------------------|------------|------------|
| **RunPod (L4)** | $28-35 | 5-15s | Moyenne |
| **RunPod (A4000)** | $24-30 | 5-15s | Moyenne |
| **Modal Labs (L4)** | $18-25 | 2-4s ⭐ | Faible |
| **Replicate** | $25-40 | 30-60s | Faible |
| **BentoML Cloud** | $20-30 | 5-15s | Moyenne |
| **Google Cloud Run GPU** | $16-20 | 5s | Moyenne |

**Modal Labs** offre les cold starts les plus rapides et $30/mois de crédits gratuits — potentiellement **gratuit** pour votre usage.

---

## 8. Exemples de code complets

### Structure du projet

```
alexandria-runpod/
├── embedder/
│   ├── handler.py
│   ├── Dockerfile
│   └── requirements.txt
├── reranker/
│   ├── handler.py
│   ├── Dockerfile
│   └── requirements.txt
└── client/
    └── runpod-client.ts
```

### Build et déploiement

```bash
# Build embedder
cd embedder
docker build -t yourusername/alexandria-embedder:1.0.0 --platform linux/amd64 .
docker push yourusername/alexandria-embedder:1.0.0

# Build reranker
cd ../reranker
docker build -t yourusername/alexandria-reranker:1.0.0 --platform linux/amd64 .
docker push yourusername/alexandria-reranker:1.0.0
```

Sur RunPod :
1. **Serverless > New Endpoint**
2. Sélectionner GPU (L4 recommandé)
3. Docker Image : `yourusername/alexandria-embedder:1.0.0`
4. Config : Max Workers = 3, Idle Timeout = 30s

### Test local du handler

```bash
# Test avec input JSON
cd embedder
python handler.py --test_input '{"input": {"texts": ["Hello world"]}}'

# Ou avec serveur local
python handler.py --rp_serve_api
# Puis: curl -X POST http://localhost:8000/run -d '{"input": {...}}'
```

---

## 9. Monitoring et debugging

### Dashboard RunPod

Métriques disponibles dans **Serverless > [Endpoint] > Metrics** :

- **Jobs** : Completed, Failed, In Queue, In Progress
- **Workers** : Idle, Running, Throttled
- **Latence** : Delay Time (queue), Execution Time
- **Cold starts** : Visible via Delay Time élevé

### Accès aux logs

```
Serverless > [Endpoint] > Logs
```

Les logs affichent :
- Stdout/stderr du handler
- Erreurs d'initialisation
- Temps de chargement des modèles

Vous pouvez aussi voir les logs d'un job spécifique via l'API :

```typescript
const status = await fetch(
  `https://api.runpod.ai/v2/${endpointId}/status/${jobId}`,
  { headers: { authorization: API_KEY } }
).then(r => r.json());

console.log(status.error);  // Si échec
```

### Alerting

RunPod n'a pas d'alerting intégré. Implémentez côté client :

```typescript
// Monitoring wrapper
async function monitoredCall<T>(fn: () => Promise<T>, name: string): Promise<T> {
  const start = Date.now();
  try {
    const result = await fn();
    const duration = Date.now() - start;
    
    // Alert si latence > 2s
    if (duration > 2000) {
      console.warn(`[SLOW] ${name}: ${duration}ms`);
      // Envoyer à votre système de monitoring
    }
    
    return result;
  } catch (error) {
    console.error(`[ERROR] ${name}:`, error);
    // Envoyer alerte
    throw error;
  }
}
```

---

## Liens documentation officielle

### RunPod
- **Pricing** : https://www.runpod.io/pricing
- **Serverless Docs** : https://docs.runpod.io/serverless/overview
- **Handler Functions** : https://docs.runpod.io/serverless/workers/handler-functions
- **API Reference** : https://docs.runpod.io/serverless/endpoints/send-requests
- **JavaScript SDK** : https://docs.runpod.io/sdks/javascript/overview
- **FlashBoot** : https://www.runpod.io/blog/introducing-flashboot-serverless-cold-start

### GitHub
- **Worker Template** : https://github.com/runpod-workers/worker-template
- **RunPod Python SDK** : https://github.com/runpod/runpod-python
- **RunPod JS SDK** : https://github.com/runpod/js-sdk

### Modèles HuggingFace
- **Qwen3-Embedding-0.6B** : https://huggingface.co/Qwen/Qwen3-Embedding-0.6B
- **bge-reranker-v2-m3** : https://huggingface.co/BAAI/bge-reranker-v2-m3

---

## Conclusion et recommandations finales

Pour votre projet Alexandria avec ~100-500 requêtes/jour :

1. **Architecture** : Déployez **2 endpoints séparés** (embedder + reranker) sur GPU **L4 (24GB)** ou **A4000 (16GB)** pour l'option la plus économique

2. **Coût estimé** : **$24-35/mois** sur RunPod, potentiellement moins avec Modal Labs

3. **Latence** : Attendez ~1-3s par requête avec workers warm, jusqu'à 15-20s sur cold start (acceptable selon vos contraintes)

4. **Optimisation prioritaire** : Bakez les modèles dans l'image Docker et configurez idle timeout à 30s pour minimiser les cold starts sans payer de workers permanents

5. **Alternative à considérer** : **Modal Labs** offre des cold starts 3x plus rapides (2-4s vs 10-15s) et $30/mois de crédits gratuits qui couvrent potentiellement tout votre usage