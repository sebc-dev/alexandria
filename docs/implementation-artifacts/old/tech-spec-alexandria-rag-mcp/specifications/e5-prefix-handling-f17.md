# E5 Prefix Handling (F17)

## Configuration transformers.js obligatoire

**⚠️ Les embeddings E5 nécessitent une normalisation L2 manuelle:**

```typescript
import { pipeline } from '@huggingface/transformers';

const extractor = await pipeline(
  'feature-extraction',
  'Xenova/multilingual-e5-small',
  { revision: 'main' }
);

// OBLIGATOIRE: pooling ET normalize
const output = await extractor(prefixedText, {
  pooling: 'mean',    // Pooling moyen sur les tokens
  normalize: true     // ⚠️ Normalisation L2 REQUISE
});

const embedding = Array.from(output.data);
```

**Sans `normalize: true`, les vecteurs n'auront pas une norme unitaire, ce qui faussera tous les calculs de similarité cosinus.**

## Format des prefixes

| Type | Prefix | Format exact | Tokens consommés |
|------|--------|--------------|------------------|
| Query (recherche) | `query: ` | "query: " + content | ~2-3 tokens |
| Passage (ingestion) | `passage: ` | "passage: " + content | ~2-3 tokens |

**⚠️ Les préfixes sont obligatoires** — sans eux, les performances de retrieval se dégradent significativement.

**⚠️ Contrainte critique: 512 tokens max**

Le modèle E5 tronque **silencieusement** tout au-delà de 512 tokens. Les prefixes comptent dans ce budget.

## Règles de gestion

1. **Ajout automatique**: Le système ajoute TOUJOURS le prefix approprié
2. **Content existant avec prefix**: Si content commence par "query:" ou "passage:", le prefix est quand même ajouté (double prefix = bug dans les données source, pas notre problème)
3. **Whitespace**: Un seul espace après le colon, pas de trim du content
4. **Token budget**: ~509 tokens effectifs après prefix (512 - ~3 pour prefix)

**Estimation et validation des tokens:**

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

// Validation avant chunking - inclut le prefix
function isChunkValid(chunk: string, maxTokens = 500): boolean {
  return countTokens(`passage: ${chunk}`) <= maxTokens;
}

// Approximations rapides (±15%)
function estimateTokens(text: string, type: 'prose' | 'code' | 'mixed'): number {
  const ratio = { prose: 4.0, code: 3.0, mixed: 3.5 }[type];
  return Math.ceil(text.length / ratio);
}
```

**Ratios caractères/tokens (XLMRobertaTokenizer):**

| Type de contenu | Ratio chars/token | Max chars (512 tokens) |
|-----------------|-------------------|------------------------|
| Anglais technique | 4.0 – 4.75 | 2,048 – 2,432 |
| Français technique | 4.6 – 4.7 | 2,355 – 2,406 |
| JavaScript/TypeScript | 3.0 – 3.5 | 1,536 – 1,792 |
| Markdown mixte | 3.0 – 4.0 | 1,536 – 2,048 |

## Fonction d'embedding complète

```typescript
import { pipeline, type Pipeline } from '@huggingface/transformers';

let extractor: Pipeline | null = null;

async function getExtractor(): Promise<Pipeline> {
  if (!extractor) {
    extractor = await pipeline(
      'feature-extraction',
      'Xenova/multilingual-e5-small',
      { revision: 'main' }
    );
  }
  return extractor;
}

/**
 * Génère un embedding normalisé avec le prefix approprié
 * @param text - Texte à encoder
 * @param type - 'query' pour recherche, 'passage' pour ingestion
 */
async function getEmbedding(
  text: string,
  type: 'query' | 'passage'
): Promise<number[]> {
  const prefixedText = `${type}: ${text}`;
  const ext = await getExtractor();

  const output = await ext(prefixedText, {
    pooling: 'mean',
    normalize: true  // ⚠️ OBLIGATOIRE
  });

  return Array.from(output.data);
}

// Usage
const docEmbedding = await getEmbedding(content, 'passage');
const queryEmbedding = await getEmbedding(question, 'query');
```

**Exemple de prefixage:**

```typescript
// Input: "Comment configurer TypeScript?"
// Query embedding: "query: Comment configurer TypeScript?"

// Input: "passage: déjà préfixé par erreur"
// Passage embedding: "passage: passage: déjà préfixé par erreur"
// (le double prefix est une erreur de l'utilisateur, pas du système)
```
