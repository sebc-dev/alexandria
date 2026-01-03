# Stratégies de chunking pour RAG sur documentation markdown technique

Pour un système RAG traitant de la documentation technique markdown avec code, la configuration optimale combine **400-512 tokens par chunk avec 10-20% d'overlap**, utilisant une stratégie **markdown-aware hiérarchique**. Les benchmarks 2024-2025 confirment que le `RecursiveCharacterTextSplitter` avec séparateurs markdown reste le choix par défaut le plus fiable, tandis que le **late chunking** de Jina AI représente l'innovation la plus prometteuse pour préserver le contexte inter-chunks. Pour TypeScript/Bun, `@langchain/textsplitters` combiné avec `gray-matter` pour le YAML front matter offre la stack la plus mature—avec la limitation importante que `MarkdownHeaderTextSplitter` n'existe qu'en Python.

## Les benchmarks 2024-2025 convergent vers 400-512 tokens

Les études récentes révèlent une fourchette optimale claire selon le type de requête. L'étude NVIDIA 2024 démontre que les **requêtes factuelles** performent mieux avec **256-512 tokens**, tandis que les **requêtes analytiques** nécessitent **1024+ tokens**. Le benchmark Chroma montre que le RecursiveCharacterTextSplitter atteint **88-89% de recall à 400 tokens**.

Anthropic recommande dans sa recherche sur le Contextual Retrieval des chunks de **800 tokens maximum** avec l'ajout d'une description contextuelle de **50-100 tokens** préfixée à chaque chunk—cette technique réduit les échecs de retrieval de **49%** (et **67%** avec reranking). Pour le modèle **Qwen3-Embedding-0.6B** ciblé, la fenêtre de contexte est de **8192 tokens**, offrant une marge confortable.

| Type de contenu | Taille recommandée | Overlap |
|-----------------|-------------------|---------|
| Documentation technique | 400-500 tokens | 10-15% |
| Requêtes factuelles | 256-384 tokens | 10% |
| Requêtes analytiques | 512-1024 tokens | 15-20% |
| Blocs de code | Fonction/classe complète | Minimal |

La recherche ArXiv "Rethinking Chunk Size for Long-Document Retrieval" révèle que différents modèles d'embedding ont des sensibilités distinctes: les modèles **Stella** bénéficient de chunks plus grands (contexte global), tandis que **Snowflake** performe mieux avec des chunks plus petits (matching fin).

## Le chunking markdown-aware hiérarchique domine pour la documentation technique

Parmi les huit stratégies étudiées, le **chunking markdown-aware** (basé sur les headers) combiné avec une approche **parent-child hiérarchique** offre le meilleur équilibre pour la documentation technique. Cette approche crée deux niveaux: des **child chunks petits** (400 tokens) pour un retrieval précis, et des **parent chunks larges** (2000 tokens) pour fournir le contexte complet au LLM.

Le **late chunking** de Jina AI (août 2024) représente l'innovation majeure: au lieu de chunker puis embedder, il **embedde le document entier** avec un modèle long-context (8K+ tokens), puis applique le chunking aux embeddings via mean pooling. Cette technique préserve les dépendances contextuelles longue-distance—par exemple, une référence "la fonction" dans le chunk 5 conserve l'information que "parseJSON" était défini dans le chunk 1. Les benchmarks Jina montrent des gains de **+2-3% nDCG@10** sur SciFact et NFCorpus.

Le **semantic chunking** basé sur la similarité d'embeddings entre phrases consécutives atteint les meilleurs scores de recall (**91.9%** pour LLMSemanticChunker selon Chroma), mais son coût computationnel élevé—embedding de chaque phrase individuellement—le rend moins pratique pour des volumes importants. Une étude ArXiv d'octobre 2024 questionne si ce coût est justifié par les gains de performance.

Le **fixed-size chunking** simple reste à éviter pour la documentation technique structurée: il ignore la sémantique et peut couper arbitrairement au milieu des blocs de code, rendant le code récupéré inutilisable.

## Les blocs de code exigent une préservation stricte des frontières

La règle cardinale pour les blocs de code est de **ne jamais couper à l'intérieur des marqueurs** ` ``` `. Pinecone confirme: "Si vous passez simplement du markdown avec du code au recursive text chunker, vous obtiendrez du code cassé." L'approche recommandée utilise des séparateurs language-aware:

```typescript
// Séparateurs pour code Python/JS
const separators = [
  "\n```\n",      // Fin de bloc de code (priorité haute)
  "\n\nclass ",   // Définitions de classes
  "\n\ndef ",    // Définitions de fonctions
  "\n\n",        // Sauts de paragraphe
  "\n",          // Sauts de ligne
  " ",           // Espaces
  ""
];
```

LangChain supporte **25+ langages** via `RecursiveCharacterTextSplitter.fromLanguage()` incluant Python, JavaScript, Rust, Go et Markdown. Pour les documents mixtes prose/code, une approche hybride route le code vers un splitter language-aware et le texte vers le recursive splitter standard.

Les **tableaux markdown** doivent idéalement être conservés entiers (sous 1000 tokens). Pour les grands tableaux, deux stratégies fonctionnent: le **row-based splitting** avec répétition des headers dans chaque chunk, ou la conversion **table-to-text** générant une description en langage naturel.

Les **headers markdown** servent de métadonnées essentielles via le pattern "breadcrumb": `Section > Sous-section > Contenu`. Cette hiérarchie enrichit les embeddings et permet le filtrage par section lors du retrieval.

## L'overlap optimal se situe entre 10-20% avec débat sur sa nécessité

Le consensus 2024-2025 recommande **10-20% d'overlap** (50-100 tokens pour des chunks de 500 tokens). Cependant, une étude Chroma révèle que dans certains tests, **l'absence d'overlap** a performé mieux en réduisant la redondance et améliorant l'IoU (Intersection over Union).

| Source | Recommandation | Contexte |
|--------|---------------|----------|
| Weaviate | 10-20% | Best practice général |
| Chroma | 0% performant | Tests spécifiques |
| NVIDIA 2024 | 50 tokens / 300 tokens | Papers académiques |
| Anthropic | 20% | Production systems |

L'overlap augmente le recall mais aussi la taille d'index—jusqu'à **60% d'augmentation** selon l'étude Chemistry RAG. La recommandation pratique: commencer avec **50-100 tokens d'overlap** pour 400-500 tokens de chunk, puis ajuster selon les métriques de votre système.

## LangChain.js offre la stack TypeScript la plus mature avec une limitation importante

Pour TypeScript sur Bun, le package `@langchain/textsplitters` est officiellement supporté avec quelques caveats sur les dépendances async queue dans les anciennes versions. L'installation se fait via `bun add @langchain/textsplitters @langchain/core`.

**Limitation critique découverte**: `MarkdownHeaderTextSplitter` n'existe **que dans LangChain Python**—il est absent de LangChain.js. Une discussion GitHub ouverte demande son ajout. Pour le header-based splitting en TypeScript, **LlamaIndex.TS avec `MarkdownNodeParser`** comble ce manque.

Le workflow recommandé combine plusieurs outils:

```typescript
import matter from 'gray-matter';
import { RecursiveCharacterTextSplitter } from '@langchain/textsplitters';

// 1. Extraire le YAML front matter
const { content, data: metadata } = matter(rawMarkdown);

// 2. Splitter avec séparateurs markdown
const splitter = RecursiveCharacterTextSplitter.fromLanguage("markdown", {
  chunkSize: 500,
  chunkOverlap: 75,
});

// 3. Créer les chunks avec métadonnées propagées
const chunks = await splitter.createDocuments([content], [metadata]);
```

**Alternatives légères Bun-compatibles**:
- `llm-text-splitter`: Pure TypeScript, supporte le mode markdown
- `llm-chunk`: API minimaliste, très léger
- `remark/unified`: Écosystème complet avec AST markdown, 150+ plugins

Le package `gray-matter` reste incontournable pour le parsing du YAML front matter, supportant YAML, JSON et TOML.

## Le format llms.txt structure naturellement le contenu pour les LLMs

La spécification **llms.txt** proposée par Jeremy Howard (Answer.AI, septembre 2024) fournit une structure markdown standardisée optimisée pour la consommation par les LLMs. Un fichier llms.txt valide contient: un **titre H1** (obligatoire), une **citation blockquote** résumant le projet, des **sections H2** listant les liens vers la documentation avec descriptions.

La différence clé entre les variantes:
- **llms.txt**: Navigation compacte avec liens et descriptions (~10KB)
- **llms-full.txt**: Contenu complet consolidé (plusieurs MB)—reçoit **2x plus de trafic AI** selon Mintlify

Pour le chunking de llms.txt, la structure naturelle dicte la stratégie: chaque **section H2** forme un chunk logique, avec titre + summary conservés comme métadonnées globales. Pour llms-full.txt, le `MarkdownTextSplitter` standard fonctionne bien avec ses séparateurs respectant les headers.

L'écosystème d'outils inclut le CLI Python officiel `llms-txt` d'Answer.AI et des plugins pour VitePress, Docusaurus et WordPress (Yoast SEO). Pour l'intégration IDE, Cursor supporte l'ajout de llms-full.txt comme contexte via @Docs.

## Configuration recommandée pour votre stack technique

Pour le cas d'usage spécifique—documentation technique markdown avec code, Qwen3-Embedding-0.6B, TypeScript/Bun, Claude Code mono-utilisateur—voici la configuration optimale:

**Paramètres de chunking**:
- Taille: **450-500 tokens** (marge pour Qwen3 à 8192 tokens)
- Overlap: **50-75 tokens** (10-15%)
- Stratégie: Two-pass chunking (headers markdown puis recursive splitting)

**Stack technique**:
- `gray-matter` pour extraction YAML front matter
- `@langchain/textsplitters` avec `fromLanguage("markdown")`
- `LlamaIndex.TS MarkdownNodeParser` pour splitting header-aware si nécessaire
- Detection regex des blocs de code pour préservation

**Métadonnées à indexer**:
- Breadcrumb de headers (H1 > H2 > H3)
- Nom du fichier source
- Tags du front matter YAML
- Langue du contenu (fr/en pour votre mix)

## Conclusion

Le chunking pour RAG documentation technique a atteint une maturité technique en 2024-2025, avec un consensus clair sur les fondamentaux: **400-512 tokens**, **10-20% overlap**, stratégie **markdown-aware respectant la structure des headers et préservant les blocs de code intacts**. L'innovation late chunking de Jina AI mérite attention pour les systèmes visant la plus haute qualité de retrieval, bien qu'elle requière des modèles d'embedding long-context.

La surprise principale pour les développeurs TypeScript reste l'absence de `MarkdownHeaderTextSplitter` dans LangChain.js—LlamaIndex.TS comble partiellement ce manque avec `MarkdownNodeParser`. Pour un système RAG mono-utilisateur développeur comme prévu, le pragmatisme recommande de commencer avec la configuration baseline (RecursiveCharacterTextSplitter markdown + gray-matter), puis d'itérer selon les métriques de recall et la pertinence des réponses de Claude.