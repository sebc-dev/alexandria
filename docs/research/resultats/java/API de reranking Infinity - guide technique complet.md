# API de reranking Infinity : guide technique complet

L'endpoint **POST /rerank** d'Infinity Embedding Server suit une spécification compatible Cohere, avec des scores normalisés par **sigmoid** produisant des valeurs entre 0 et 1. Pour le modèle **bge-reranker-v2-m3**, la limite clé est de **512 tokens** pour la combinaison query+document. Infinity gère automatiquement la troncation et retourne les résultats triés par pertinence décroissante. L'intégration avec Alexandria RAG Server nécessite de comprendre que les scores bruts sont des logits transformés en probabilités — un score de 0.5+ indique une haute pertinence.

## Schéma OpenAPI du endpoint /rerank

### Requête HTTP

```
POST /rerank
POST /v1/rerank  (alternative avec préfixe v1)
Content-Type: application/json
Accept: application/json
Authorization: Bearer <api-key>  (optionnel, si --api-key configuré)
```

### Schéma du body JSON (Request)

```json
{
  "model": "BAAI/bge-reranker-v2-m3",
  "query": "string",
  "documents": ["string", "string", ...],
  "top_n": 3,
  "return_documents": false,
  "raw_scores": false
}
```

| Paramètre | Type | Requis | Défaut | Description |
|-----------|------|--------|--------|-------------|
| `model` | `string` | **Oui** | — | ID du modèle déployé (ex: `BAAI/bge-reranker-v2-m3`) |
| `query` | `string` | **Oui** | — | Requête de recherche à comparer |
| `documents` | `array[string]` | **Oui** | — | Liste de documents textuels à reranker |
| `top_n` | `integer` | Non | Tous | Nombre maximum de résultats à retourner |
| `return_documents` | `boolean` | Non | `false` | Inclure le texte des documents dans la réponse |
| `raw_scores` | `boolean` | Non | `false` | Paramètre provider-specific (non transformé par sigmoid) |

### Schéma de la réponse JSON (Response)

```json
{
  "object": "rerank",
  "results": [
    {
      "relevance_score": 0.9948403768,
      "index": 2,
      "document": null
    },
    {
      "relevance_score": 0.8872248530,
      "index": 0,
      "document": null
    }
  ],
  "model": "BAAI/bge-reranker-v2-m3",
  "usage": {
    "prompt_tokens": 2282,
    "total_tokens": 2282
  },
  "id": "infinity-9d00343b-20a9-4d69-9367-438a68bc08cb",
  "created": 1742163022
}
```

| Champ | Type | Description |
|-------|------|-------------|
| `object` | `string` | Toujours `"rerank"` |
| `results` | `array[RerankResult]` | **Triés par relevance_score décroissant** |
| `results[].relevance_score` | `float` | Score de pertinence [0-1] (sigmoid appliqué) |
| `results[].index` | `integer` | Index original du document dans l'input |
| `results[].document` | `string \| null` | Texte du document (si `return_documents=true`) |
| `model` | `string` | Modèle utilisé |
| `usage.prompt_tokens` | `integer` | Tokens traités |
| `id` | `string` | UUID unique de la requête |
| `created` | `integer` | Timestamp Unix |

## Exemple curl fonctionnel pour Alexandria

```bash
curl -X POST 'https://your-runpod-endpoint.runpod.ai/rerank' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer YOUR_API_KEY' \
  -d '{
    "model": "BAAI/bge-reranker-v2-m3",
    "query": "Quels sont les avantages du machine learning?",
    "documents": [
      "Le machine learning permet d automatiser des tâches complexes et d améliorer la précision des prédictions.",
      "La météo de Paris sera ensoleillée demain.",
      "L apprentissage automatique révolutionne la détection de fraudes et la personnalisation.",
      "Les réseaux de neurones sont une sous-catégorie du deep learning."
    ],
    "top_n": 3,
    "return_documents": true
  }'
```

**Réponse attendue:**
```json
{
  "object": "rerank",
  "results": [
    {
      "relevance_score": 0.9234,
      "index": 0,
      "document": "Le machine learning permet d automatiser des tâches complexes..."
    },
    {
      "relevance_score": 0.8756,
      "index": 2,
      "document": "L apprentissage automatique révolutionne la détection de fraudes..."
    },
    {
      "relevance_score": 0.4123,
      "index": 3,
      "document": "Les réseaux de neurones sont une sous-catégorie du deep learning."
    }
  ],
  "model": "BAAI/bge-reranker-v2-m3",
  "usage": {"prompt_tokens": 847, "total_tokens": 847},
  "id": "infinity-abc12345-...",
  "created": 1735984800
}
```

## Limites de l'API et contraintes techniques

Les limites d'Infinity ne sont pas toutes documentées explicitement mais peuvent être déduites du code source et des benchmarks communautaires.

| Limite | Valeur | Source |
|--------|--------|--------|
| **Max tokens par paire** | **512 tokens** (query + document combinés) | Model card bge-reranker-v2-m3 |
| **Troncation** | Automatique avec `truncation=True` | Comportement interne d'Infinity |
| **Max documents/requête** | Non limité explicitement | Limité par RAM/VRAM disponible |
| **Batch size recommandé** | 32 (défaut), jusqu'à 128 pour GPU haute VRAM | Benchmarks AMD MI300x |
| **Timeout** | Configurable côté RunPod | Non géré par Infinity directement |

**Performance benchmarkée** : jusqu'à **4,836 rerank/sec** (batch_size=32, tokens courts) et **~242 rerank/sec** pour contextes longs (512 tokens). Ces chiffres excluent l'overhead API et le batching dynamique.

### Comportement de troncation

Infinity utilise le tokenizer du modèle avec `max_length=512` et `truncation=True`. Si query + document dépassent 512 tokens, le texte est automatiquement tronqué. Pour des documents longs, il est recommandé de pré-découper en chunks avant d'appeler le reranker.

## Normalisation des scores et interprétation

La compréhension des scores est critique pour le filtrage dans le pipeline RAG. Le modèle **bge-reranker-v2-m3** produit des **logits bruts** (valeurs non bornées, potentiellement négatives), qu'Infinity transforme via **sigmoid** pour obtenir des scores [0-1].

### Conversion logits → scores normalisés

```python
# Exemples de conversion (interne à Infinity)
logit_raw = -8.1875   →  sigmoid(-8.1875) = 0.00028  (non pertinent)
logit_raw = 5.2617    →  sigmoid(5.2617)  = 0.9948   (très pertinent)
logit_raw = -5.6523   →  sigmoid(-5.6523) = 0.0035   (peu pertinent)
```

### Distribution typique des scores

Exemple réel avec query "Where does John live and where does he work?":

| Document | Score normalisé |
|----------|-----------------|
| "John lives in New York with his parents..." | **0.8457** |
| "John hails from New York." | 0.3760 |
| "John is from New York" | 0.2132 |
| "John is a software engineer at Apple." | 0.0418 |
| "New York is a bustling metropolis..." | 0.0027 |

### Seuils recommandés pour Alexandria RAG

**Recommandation officielle** : utiliser **Top-K filtering** plutôt qu'un seuil absolu. Les seuils varient selon le domaine et la longueur des textes.

| Stratégie | Seuil suggéré | Cas d'usage |
|-----------|---------------|-------------|
| **Top-K (recommandé)** | N/A | Garder les K meilleurs résultats |
| Haute précision | score > **0.5** | Documents très pertinents uniquement |
| Précision modérée | score > **0.3** | Équilibre précision/rappel |
| Rappel élevé | score > **0.1** | Maximiser les candidats |

Pour déterminer un seuil optimal spécifique à Alexandria, il est conseillé de collecter 30-50 requêtes représentatives du domaine, de passer des documents "borderline" et d'utiliser la moyenne des scores comme référence.

## Intégration avec Langchain4j et Java 25

Pour l'intégration Java dans Alexandria, voici la structure de requête/réponse à implémenter :

### Modèle de requête (Java record)

```java
public record RerankRequest(
    String model,
    String query,
    List<String> documents,
    @Nullable Integer topN,           // top_n dans JSON
    @Nullable Boolean returnDocuments, // return_documents
    @Nullable Boolean rawScores        // raw_scores
) {}
```

### Modèle de réponse

```java
public record RerankResponse(
    String object,
    List<RerankResult> results,
    String model,
    Usage usage,
    String id,
    long created
) {
    public record RerankResult(
        double relevanceScore,  // relevance_score (snake_case dans JSON)
        int index,
        @Nullable String document
    ) {}
    
    public record Usage(int promptTokens, int totalTokens) {}
}
```

**Point d'attention** : Les résultats sont **pré-triés par score décroissant**. L'index original permet de mapper vers les documents candidats du Top-K initial.

## Erreurs courantes et troubleshooting

| Erreur | Cause | Solution |
|--------|-------|----------|
| `ModelNotDeployedError: model does not support rerank` | Modèle d'embedding utilisé au lieu de reranker | Vérifier que le modèle est `AutoModelForSequenceClassification` |
| `Cannot handle batch sizes > 1 if no padding token` | Certains modèles wrapped | Utiliser modèle officiel ou ajouter padding token |
| HTTP 500 sur /rerank | Modèle incompatible | Vérifier `/models` endpoint pour capabilities |

### Vérification des capabilities

```bash
curl http://your-endpoint/models
# Doit retourner: "capabilities": ["rerank"]
```

## Conclusion

L'intégration du reranking Infinity dans Alexandria RAG Server repose sur trois points clés : **512 tokens max** par paire query-document (gérer le chunking en amont), **scores sigmoid [0-1]** avec tri décroissant automatique, et **filtrage Top-K** plutôt que seuil absolu. Pour RunPod serverless, prévoir un timeout adapté car le cold start peut ajouter de la latence. Le modèle bge-reranker-v2-m3 offre un excellent compromis performance/qualité pour le multilingual, avec une taille de 568M paramètres permettant une inférence rapide même sur GPU modest.