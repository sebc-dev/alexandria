# Contraintes techniques du projet Alexandria RAG MCP Server

Le modèle **multilingual-e5-small** impose une limite stricte de **512 tokens** — toute donnée au-delà est silencieusement tronquée. Une configuration de chunks à 8000 caractères entraînerait donc la perte de **75% du contenu** de chaque chunk. La configuration actuelle nécessite une révision significative pour aligner les paramètres de chunking avec les capacités réelles du modèle.

## Spécifications confirmées du modèle multilingual-e5-small

Le modèle intfloat/multilingual-e5-small (dont Xenova/multilingual-e5-small est la conversion ONNX) présente des caractéristiques précises documentées par ses auteurs. La **limite maximale est de 512 tokens** — confirmée explicitement dans la documentation officielle Hugging Face et le paper technique (arXiv:2402.05672). Le modèle produit des **embeddings de 384 dimensions** via 12 couches transformer avec un hidden size de 384.

Le tokenizer utilisé est **XLMRobertaTokenizer**, basé sur SentencePiece avec l'algorithme Unigram Language Model. Ce tokenizer possède un vocabulaire de **250 000 tokens** couvrant 100 langues. Les ratios caractères/tokens varient significativement selon le contenu :

| Type de contenu | Ratio chars/token | Max chars pour 512 tokens |
|-----------------|-------------------|---------------------------|
| Anglais technique | 4.0 – 4.75 | **2,048 – 2,432** |
| Français technique | 4.6 – 4.7 | **2,355 – 2,406** |
| JavaScript/TypeScript | 3.0 – 3.5 | **1,536 – 1,792** |
| Markdown mixte (EN/FR + code) | 3.0 – 4.0 | **1,536 – 2,048** |

Pour du contenu technique multilingue avec du code, la limite pratique se situe entre **1,500 et 1,800 caractères** pour rester sous les 512 tokens avec une marge de sécurité. Les préfixes obligatoires `query: ` et `passage: ` consomment environ **2-3 tokens** chacun sur ce budget.

## L'erreur critique de la configuration actuelle des chunks

Une taille de chunk de **8000 caractères représente environ 2,000-2,600 tokens** selon le contenu — soit **4 à 5 fois la capacité du modèle**. Le modèle tronque silencieusement tout au-delà de 512 tokens, ce qui signifie que seuls les **premiers 1,500-2,000 caractères** de chaque chunk sont réellement encodés dans l'embedding. Le reste est purement ignoré.

Ce problème a des conséquences directes sur la qualité du RAG : l'embedding ne représente qu'une fraction du chunk stocké, créant une **dissonance entre ce qui est cherché et ce qui est retourné**. Un utilisateur cherchant une information située dans la seconde moitié d'un chunk ne la trouvera jamais car cette partie n'est pas représentée dans le vecteur.

Les benchmarks 2024-2025 de Firecrawl, Microsoft Azure AI Search et LlamaIndex convergent vers une recommandation de **300-500 tokens** (1,200-2,000 caractères) pour les modèles d'embedding à 384 dimensions. Cette taille offre le meilleur compromis entre granularité sémantique et préservation du contexte.

## Configuration recommandée pour le chunking

La configuration optimale pour votre stack technique (Bun, PostgreSQL 18.1, pgvector 0.8.1) devrait être :

| Paramètre | Valeur actuelle | Valeur recommandée | Justification |
|-----------|-----------------|-------------------|---------------|
| **Chunk size** | 8,000 chars | **1,600 – 1,800 chars** | Reste sous 512 tokens avec marge |
| **Warning threshold** | 4,000 chars | **1,500 chars** | Alerte avant d'approcher la limite |
| **Hard limit** | — | **2,000 chars** | Ne jamais dépasser |
| **Chunk overlap** | ? | **150-200 chars** (10-12%) | Standard pour docs structurés |
| **Max chunks/doc** | 500 | **500** ✓ | Réaliste et conservateur |

Le seuil de warning à 4,000 caractères correspond à ~1,000 tokens — déjà **2 fois la limite du modèle**. Il serait pertinent de le baisser à **1,500 caractères** pour alerter dès qu'on approche de la zone critique. La limite de 500 chunks par document est raisonnable : elle permet d'indexer des documents de ~900,000 caractères (~225,000 tokens) tout en évitant les problèmes de mémoire et timeouts.

Pour le splitting Markdown, utilisez une hiérarchie de séparateurs respectant la structure : `["\n## ", "\n### ", "\n```", "\n\n", "\n", " "]`. Cela préserve l'intégrité des blocs de code et des sections tout en permettant des splits propres aux frontières sémantiques naturelles.

## Configuration pgvector et le paramètre ef_search

Le paramètre `ef_search` dans l'implémentation HNSW de pgvector contrôle la **taille de la liste de candidats** explorés pendant la recherche. Plus la valeur est élevée, plus l'algorithme explore le graphe HNSW, améliorant le recall au détriment de la latence.

Avec pgvector **avant 0.8.0**, `ef_search` limitait directement le nombre maximum de résultats retournables. Avec **pgvector 0.8.0+**, cette contrainte est assouplie grâce aux *iterative index scans* qui permettent de récupérer plus de résultats si nécessaire. Votre configuration `ef_search=60` avec un maximum de 60 résultats est **techniquement cohérente** mais sous-optimale.

La règle d'or recommandée par la documentation officielle : **ef_search ≥ 2× LIMIT**. Pour 60 résultats maximum, visez `ef_search = 120`. Cela fournit un pool de candidats plus large pour un meilleur recall sans impact majeur sur les performances.

Configuration recommandée pour pgvector 0.8.1 :

```sql
SET hnsw.ef_search = 120;              -- 2x le max de résultats
SET hnsw.iterative_scan = relaxed_order; -- Important si vous filtrez
```

| ef_search | Recall estimé | Latence relative | Usage |
|-----------|--------------|------------------|-------|
| 40 (défaut) | ~85-90% | Baseline | Haute throughput |
| 100 | ~92-95% | 2.5× | Production standard |
| 120-150 | ~95-97% | 3-4× | **Recommandé pour votre cas** |
| 200+ | ~98%+ | 5× | Précision critique |

## Validation des limites MCP tool schemas

**Query max 1000 caractères** : Cette limite est **largement suffisante** pour la recherche sémantique. Une query de 1,000 chars représente ~250 tokens — bien en dessous de la capacité du modèle et couvrant des requêtes très détaillées. Les recherches sémantiques efficaces dépassent rarement 200-300 caractères. Cette limite protège contre les abus tout en permettant des queries complexes avec contexte.

**Source path max 500 caractères avec regex `^[a-zA-Z0-9/_.-]{1,500}$`** : Cette configuration est **appropriée et sécurisée**. Les chemins de fichiers dépassent rarement 256 caractères (limite historique de nombreux systèmes de fichiers). Le regex autorise les caractères essentiels pour les chemins Unix/POSIX tout en bloquant les caractères dangereux pour l'injection. Considérez d'ajouter le tiret bas `_` si ce n'est pas déjà le cas.

**Title max 200 caractères** : C'est un **standard de l'industrie**. Les titres HTML sont typiquement limités à 50-70 caractères pour le SEO, les en-têtes de documents rarement au-delà de 100 caractères. La limite de 200 caractères offre une marge confortable pour les titres techniques descriptifs tout en prévenant les abus.

## Outils pour l'estimation précise des tokens

Pour estimer précisément le nombre de tokens **avant** l'encoding, utilisez directement le tokenizer du modèle :

```typescript
// Avec @huggingface/transformers dans Bun
import { AutoTokenizer } from '@huggingface/transformers';

const tokenizer = await AutoTokenizer.from_pretrained(
  'Xenova/multilingual-e5-small'
);

function countTokens(text: string): number {
  const encoded = tokenizer.encode(text);
  return encoded.length;
}

function getCharTokenRatio(text: string): number {
  const tokens = tokenizer.encode(text);
  return text.length / tokens.length;
}

// Validation avant chunking
function isChunkValid(chunk: string, maxTokens = 500): boolean {
  return countTokens(`passage: ${chunk}`) <= maxTokens;
}
```

Pour des **approximations rapides** sans appel au tokenizer :
- Anglais/Français : divisez par **4.0** (chars ÷ 4 = tokens estimés)
- Code : divisez par **3.0**
- Markdown mixte : divisez par **3.5**

Ces approximations restent valides à ±15% pour la plupart des contenus techniques.

## Conclusion et recommandations d'action

La configuration actuelle du projet Alexandria présente un **désalignement critique** entre la taille des chunks (8,000 chars) et la capacité du modèle (512 tokens ≈ 1,500-2,000 chars). Trois modifications prioritaires s'imposent :

1. **Réduire immédiatement la taille des chunks** à 1,600-1,800 caractères maximum, avec un overlap de 150-200 caractères pour maintenir la cohérence contextuelle aux frontières

2. **Ajuster ef_search à 120** minimum pour votre limite de 60 résultats, et activer `hnsw.iterative_scan` pour les queries avec filtres

3. **Implémenter une validation par tokenization réelle** avant l'indexation plutôt que de se fier aux approximations caractères — le ratio varie trop selon le contenu (code vs prose vs multilingue)

Les limites MCP (query 1000, path 500, title 200) sont appropriées et peuvent être conservées. La limite de 500 chunks par document est réaliste pour éviter les problèmes de ressources tout en supportant des documents techniques de taille significative.