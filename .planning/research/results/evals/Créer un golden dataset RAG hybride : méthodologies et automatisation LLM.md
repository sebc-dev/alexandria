# Créer un golden dataset RAG hybride : méthodologies et automatisation LLM

Un golden dataset de **200 Q&A** pour un système RAG hybride (vector + knowledge graph) peut être créé en **3 à 5 jours** par un développeur solo en combinant génération LLM automatisée et validation humaine ciblée. La clé réside dans une structure de données enrichie, une taxonomie de questions adaptée au graph, et l'utilisation d'outils comme RAGAS ou Distilabel avec des modèles open-source locaux (Ollama). Pour l'évaluation en environnement Java/LangChain4j, l'extension **Quarkus LangChain4j Testing** offre une solution native, tandis que les frameworks Python (RAGAS, DeepEval) nécessitent un wrapper REST ou subprocess.

---

## Structure optimale d'un golden dataset RAG hybride

La structure minimale consensuelle comprend quatre champs : `question`, `contexts`, `ground_truth` et `answer`. Pour un système hybride PostgreSQL + pgvector + Apache AGE, cette structure doit s'enrichir de métadonnées spécifiques au knowledge graph.

Le schéma JSON recommandé intègre des champs pour les **entités et relations KG attendues**, permettant d'évaluer séparément la contribution du graph traversal. Chaque sample doit inclure un indicateur `requires_kg` (boolean) et le nombre de `reasoning_hops` requis, facilitant l'analyse par type de requête.

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "question", "ground_truth", "expected_contexts"],
  "properties": {
    "id": {"type": "string"},
    "question": {
      "type": "object",
      "properties": {
        "text": {"type": "string"},
        "type": {"enum": ["fact_single", "fact_multi", "summary", "reasoning", 
                         "comparison", "temporal", "aggregation", "graph_traversal", 
                         "multi_hop", "unanswerable"]},
        "difficulty": {"enum": ["easy", "medium", "hard"]},
        "reasoning_hops": {"type": "integer", "minimum": 1, "maximum": 5},
        "requires_kg": {"type": "boolean"}
      }
    },
    "ground_truth": {
      "type": "object",
      "properties": {
        "answer_text": {"type": "string"},
        "answer_type": {"enum": ["extractive", "abstractive", "boolean", "list", "numeric"]},
        "key_facts": {"type": "array", "items": {"type": "string"}}
      }
    },
    "expected_contexts": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "chunk_id": {"type": "string"},
          "content": {"type": "string"},
          "source_document": {"type": "string"},
          "relevance_label": {"type": "integer", "minimum": 0, "maximum": 2}
        }
      }
    },
    "expected_kg_elements": {
      "type": "object",
      "properties": {
        "entities": {"type": "array", "items": {"type": "string"}},
        "relations": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "subject": {"type": "string"},
              "predicate": {"type": "string"},
              "object": {"type": "string"}
            }
          }
        }
      }
    },
    "metadata": {
      "type": "object",
      "properties": {
        "created_at": {"type": "string", "format": "date-time"},
        "created_by": {"enum": ["expert", "synthetic_llm", "production"]},
        "domain": {"type": "string"},
        "validation_status": {"enum": ["draft", "reviewed", "validated"]}
      }
    }
  }
}
```

Les champs additionnels recommandés incluent `temporal_sensitivity` pour les questions dépendantes du temps, `evidence_count` pour les questions d'agrégation, et `ambiguity_level` pour tester la robustesse.

---

## Taxonomie des questions pour RAG hybride

La recherche établit deux taxonomies complémentaires : celle d'IBM Research "Know Your RAG" basée sur la **nature de la réponse** (fact_single, summary, reasoning, unanswerable), et celle de RAGAS basée sur la **complexité de la requête** (single-hop vs multi-hop, specific vs abstract).

### Distribution recommandée pour 200 Q&A

| Catégorie | Proportion | Nombre | Description |
|-----------|------------|--------|-------------|
| Factuelles simples | 30% | 60 | Lookup direct, définitions, valeurs numériques |
| Multi-hop 2 étapes | 15% | 30 | Entity bridging, composition simple |
| Multi-hop 3+ étapes | 10% | 20 | Chaînes de raisonnement complexes |
| Graph traversal | 15% | 30 | Relations, chemins, voisins communs |
| Comparatives | 10% | 20 | Différences, similitudes, classements |
| Temporelles | 5% | 10 | Chronologie, "avant/après" |
| Agrégation | 5% | 10 | Comptage, sommes, listes |
| Edge cases | 10% | 20 | Sans réponse, ambiguës, adversariales |

Les questions **graph-specific** testent des patterns impossibles à résoudre efficacement par vector search seul : shortest path ("Comment A est-il connecté à B ?"), common neighbors ("Quels domaines A et B partagent-ils ?"), et temporal chains ("Quels événements ont précédé X ?").

### Patterns de questions KG avec exemples

Les questions multi-hop suivent des patterns documentés dans les benchmarks HotpotQA et MuSiQue. Le pattern **entity bridging** connecte deux entités via un nœud intermédiaire : "The MVP played for what team that Player X also joined?" nécessite deux traversées (MVP→Player→Team(date)). Le pattern **composition** combine plusieurs contraintes : "Films directed by X and released after 2020 with budget over $100M".

La synergie vector + graph se manifeste particulièrement pour les questions globales nécessitant une vue d'ensemble. Les études HybridRAG (BlackRock/NVIDIA) démontrent que l'approche hybride atteint une **faithfulness de 0.96** contre 0.94 pour le vector seul, avec un gain plus marqué sur l'answer relevancy (**0.96 vs 0.91**).

---

## Génération automatique par LLM : workflow et prompts

L'approche **context-first** (document→question) est recommandée pour RAG : partir des chunks de documentation et générer des questions répondables par ce contexte garantit la traçabilité et élimine les questions hors-scope.

### Workflow en 7 étapes pour 200 Q&A

**Étape 1 - Chunking intelligent** (~30 min)
```python
from langchain.text_splitter import RecursiveCharacterTextSplitter
splitter = RecursiveCharacterTextSplitter(
    chunk_size=1000, chunk_overlap=100,
    separators=["\n\n", "\n", ". ", " "]
)
# Viser 50-70 chunks pour 200 Q&A (3-4 questions/chunk)
```

**Étape 2 - Génération brute** (2-4h selon hardware)
Générer **300 Q&A brutes** (surplus de 50% pour filtrage ultérieur) avec le prompt suivant :

```
Based on the following technical documentation, generate a question and answer pair.
The question should be specific and answerable ONLY from this text.
Vary the question type: factual, reasoning, comparison, or multi-step.

Text: {chunk}

Return ONLY valid JSON: {"question": "...", "answer": "...", "type": "..."}
```

**Étape 3 - Évolution des questions** (1-2h)
Appliquer la technique **Evol-Instruct** sur 50% des questions pour augmenter la complexité :

```
I want you to rewrite the given question to require multi-step reasoning.

1. The rewritten question should require multiple logical connections or inferences.
2. It must remain answerable from the provided context.
3. Do not use phrases like "based on the provided context."
4. Limit to 15 words maximum.

Context: {context}
Original Question: {question}
Rewritten Question:
```

**Étape 4 - Génération de questions multi-hop KG** (1h)
Pour les 30 questions graph-specific, utiliser un prompt ciblé :

```
Given this knowledge graph excerpt and the related text, generate a question that requires 
traversing at least 2 relationships to answer.

Entities: {entities_list}
Relations: {relations_list}
Context: {text}

The question should:
- Require connecting information from multiple nodes
- Be naturally phrased (no graph jargon)
- Have a clear, verifiable answer

Return JSON: {"question": "...", "answer": "...", "required_path": [...], "hops": N}
```

**Étape 5 - Déduplication sémantique** (~15 min)
Seuil de similarité cosinus recommandé : **0.85**

```python
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity

model = SentenceTransformer('all-MiniLM-L6-v2')
embeddings = model.encode([qa['question'] for qa in raw_qa])
similarity_matrix = cosine_similarity(embeddings)

# Filtrer paires avec similarité > 0.85
unique_indices = []
for i in range(len(raw_qa)):
    is_duplicate = any(similarity_matrix[i][j] > 0.85 for j in unique_indices)
    if not is_duplicate:
        unique_indices.append(i)
```

**Étape 6 - Validation LLM-as-Judge** (1h)
Filtrer avec score ≥ 3.5/5 sur les critères : answerability, groundedness, relevance, clarity, difficulty.

```
Evaluate this Q&A pair for a RAG evaluation dataset.

Question: {question}
Expected Answer: {answer}
Source Context: {context}

Score each criterion (1-5):
1. Answerability: Can this question be answered using ONLY the context?
2. Groundedness: Is the answer factually grounded in the context?
3. Relevance: Is the question relevant to the context topic?
4. Clarity: Is the question clearly formulated?
5. Difficulty: Is this appropriately challenging (not trivial)?

Return JSON: {"scores": {...}, "overall": X, "decision": "KEEP|REJECT", "reasoning": "..."}
```

**Étape 7 - Validation humaine et export** (2-3 jours)
Réviser manuellement ~250 Q&A filtrées, garder les 200 meilleures, exporter en JSONL.

### Stratégies anti-biais documentées

La **déduplication sémantique** avec SemDeDup (seuil R=0.75) apporte 20% d'efficacité d'entraînement et +2% d'accuracy downstream. Pour la diversité, utiliser **temperature 0.7-1.0** avec top-p sampling (P=0.9-0.95).

Le **clustering par embeddings** (K-Means sur les questions) permet de sélectionner des représentants de chaque cluster thématique, garantissant une couverture uniforme du corpus.

Le biais **self-enhancement** (même LLM génère et évalue) se mitigue en utilisant des modèles différents : générer avec Llama 3 → évaluer avec Mistral, ou générer avec Claude → évaluer avec GPT-4o.

---

## Frameworks d'évaluation RAG : matrice de décision

### Comparatif détaillé

| Critère | RAGAS | DeepEval | TruLens | Quarkus LangChain4j |
|---------|-------|----------|---------|---------------------|
| **Langage natif** | Python | Python | Python | Java |
| **Licence** | Apache 2.0 | Apache 2.0 | MIT | Apache 2.0 |
| **Métriques RAG** | 20+ | 14+ | 6+ | Personnalisable |
| **Génération dataset** | Oui | Oui | Oui | Non natif |
| **CI/CD intégré** | Script custom | `deepeval test run` | Script custom | JUnit 5 natif |
| **Compatibilité Java** | Subprocess/REST | Subprocess/REST | Subprocess/REST | **Natif** |
| **Open-source local** | Ollama supporté* | Ollama supporté | Ollama supporté | Ollama supporté |

*Note : Des issues sont reportées avec Ollama pour la génération de testsets RAGAS (recommandé : modèles ≥7B paramètres).

### Métriques clés définies précisément

**Faithfulness** mesure la cohérence factuelle entre réponse et contexte : `Claims supportés / Total claims`. Score 1.0 = aucune hallucination, seuil recommandé ≥ 0.85.

**Context Precision** évalue le ranking des chunks : les documents pertinents sont-ils en haut ? Calcul : `Mean(Precision@K × vk)` où vk=1 si le k-ième chunk est pertinent.

**Context Recall** mesure la couverture : `Claims du ground truth retrouvables dans le contexte / Total claims`. Seule métrique nécessitant un ground truth annoté.

**Answer Relevancy** utilise une technique ingénieuse : générer N questions synthétiques à partir de la réponse, puis calculer la similarité cosinus moyenne avec la question originale.

### Recommandation pour Java/Spring Boot/LangChain4j

**Solution principale : Quarkus LangChain4j Testing Extension**

Cette extension offre l'intégration JUnit 5 native avec des stratégies d'évaluation configurables :

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-testing-evaluation-junit5</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-testing-evaluation-semantic-similarity</artifactId>
    <scope>test</scope>
</dependency>
```

```java
@QuarkusTest
@Evaluate
public class RAGEvaluationTest {
    @Inject RAGService ragService;

    @Test
    void evaluateRAG(
        @ScorerConfiguration(concurrency = 5) Scorer scorer,
        @SampleLocation("src/test/resources/golden_dataset.yaml") Samples<String> samples
    ) {
        EvaluationReport report = scorer.evaluate(
            samples,
            params -> ragService.query(params.get(0)),
            new SemanticSimilarityStrategy(0.8)
        );
        assertThat(report.score()).isGreaterThanOrEqualTo(75.0);
    }
}
```

**Alternative hybride** : Pour les métriques RAGAS avancées (faithfulness LLM-based), créer un microservice Python exposant une API REST autour de RAGAS/DeepEval, appelé depuis les tests Java.

### Intégration CI/CD

```yaml
# .github/workflows/rag-eval.yml
name: RAG Evaluation
on: [push]
jobs:
  evaluate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with: {java-version: '21', distribution: 'temurin'}
      - name: Run RAG evaluations
        run: ./mvnw test -Dtest=*RAGEvaluation*
      - name: Check score threshold
        run: |
          SCORE=$(jq '.score' target/evaluation-report.json)
          [ $(echo "$SCORE >= 75" | bc) -eq 1 ] || exit 1
```

---

## Bonnes pratiques et pièges documentés

### Taille optimale : 200 Q&A est approprié

Les retours Microsoft Data Science indiquent qu'environ **100 Q&A suffisent** pour une diversité correcte, avec un minimum de 50 pour un POC. Un dataset de 200 est donc **supérieur aux recommandations** et permet une évaluation robuste d'un système hybride. L'enjeu n'est pas la quantité mais la **diversité des types de questions** et la couverture du corpus.

### Data leakage et overfitting

Le data leakage en RAG prend plusieurs formes : fuite d'éléments du prompt d'évaluation vers le système, optimisation sur les métriques mesurées au détriment de la qualité réelle, et self-enhancement bias.

**Mitigations essentielles** :
- Ne jamais tuner les hyperparamètres sur le golden set complet
- Garder **20% en holdout strict** non utilisé pour le développement
- Utiliser des modèles différents pour génération vs évaluation
- Comparer régulièrement les performances golden set vs requêtes production réelles
- Ajouter progressivement des cas de production au dataset

### Maintenance et versioning

Fréquence de mise à jour recommandée : **toutes les 2-4 semaines** pour systèmes critiques, minimum trimestriel. Le trigger principal est l'évolution significative du corpus source.

Structure de versioning simple pour développeur solo :
```
golden_dataset/
├── v1.0/
│   ├── qa_pairs.jsonl
│   ├── metadata.json
│   └── CHANGELOG.md
├── v1.1/
└── current -> v1.1
```

### Pièges fréquents à éviter

**Questions trop faciles** : Les LLMs génèrent souvent des questions simplistes paraphrasant directement le texte. Mitigation : appliquer Evol-Instruct, filtrer par score de difficulté.

**Biais de sélection documentaire** : Dataset déséquilibré ne reflétant pas l'usage réel. Mitigation : échantillonner selon la distribution attendue en production, inclure les zones "froides" du corpus.

**Réponses gold ambiguës** : Plusieurs réponses correctes possibles, scoring incohérent. Mitigation : inclure une justification/rationale avec chaque réponse, définir des critères de scoring précis.

**Métriques trompeuses** : Scores parfaits sur faithfulness ≠ satisfaction utilisateur. Mitigation : mesurer aussi ton, clarté, complétude ; validation humaine périodique sur échantillon.

---

## Estimation effort/temps pour 200 Q&A

| Étape | Temps | Coût API |
|-------|-------|----------|
| Préparation (setup Ollama, chunking) | 1.5h | $0 |
| Génération brute LLM (500 Q&A) | 2-4h | $5-15* |
| Évolution Evol-Instruct (250 Q&A) | 1-2h | $3-8* |
| Déduplication sémantique | 15 min | $0 |
| Validation LLM-as-Judge | 1h | $5-10* |
| Validation humaine (~250 Q&A) | 16-24h | $0 |
| Documentation et export | 2h | $0 |
| **Total** | **3-5 jours** | **$13-33** |

*Avec modèles open-source locaux (Ollama Llama 3 8B), le coût API tombe à $0 mais le temps de génération augmente (×2-3).

---

## Checklist actionable

### Avant création
- [ ] Définir le use case précis et les types de requêtes attendues
- [ ] Choisir des modèles différents pour génération vs évaluation
- [ ] Installer Ollama + Llama 3 8B + nomic-embed-text
- [ ] Préparer le chunking du corpus (50-70 chunks pour 200 Q&A)

### Pendant création
- [ ] Générer 500 Q&A brutes avec prompt context-first
- [ ] Appliquer Evol-Instruct sur 50% pour complexification
- [ ] Générer 30 questions graph-specific avec prompt multi-hop
- [ ] Dédupliquer (seuil cosinus 0.85)
- [ ] Filtrer avec LLM-as-Judge (score ≥ 3.5/5)
- [ ] Inclure 10% de cas "sans réponse" attendus
- [ ] Valider manuellement 20% échantillon

### Qualité dataset
- [ ] Distribution : 30% simple, 15% 2-hop, 10% 3+hop, 15% graph, reste varié
- [ ] Couverture : toutes zones du corpus représentées
- [ ] Difficulté : inclure "hard negatives" (contexte trompeur)
- [ ] Métadonnées : type, difficulté, requires_kg, reasoning_hops

### Anti-overfitting
- [ ] Garder 20% en holdout strict (40 Q&A)
- [ ] Ne jamais tuner sur le golden set complet
- [ ] Comparer golden set vs requêtes production mensuellement
- [ ] Ajouter 5-10 cas production réels chaque mois

### Maintenance
- [ ] Versionner en Git (JSONL + metadata.json + CHANGELOG.md)
- [ ] Planifier revue trimestrielle
- [ ] Mettre à jour quand le corpus source évolue significativement

---

## Conclusion

La création d'un golden dataset de 200 Q&A pour un RAG hybride vector + knowledge graph est réalisable en **3-5 jours** par un développeur solo avec l'approche hybride LLM + validation humaine. Les points clés sont : une structure de données enrichie incluant les éléments KG attendus, une taxonomie de questions couvrant les patterns graph-specific (multi-hop, entity bridging, path queries), et une validation multi-critères avec LLM-as-Judge.

Pour l'environnement Java/LangChain4j, l'extension **Quarkus LangChain4j Testing** offre la solution la plus intégrée avec support JUnit 5 natif. Les frameworks Python (RAGAS, DeepEval) restent pertinents pour leurs métriques avancées mais nécessitent un bridge REST. L'utilisation de modèles open-source locaux via Ollama élimine les coûts API tout en maintenant une qualité suffisante pour la génération de datasets.

Le piège majeur à éviter est l'**overfitting au golden set** : réserver 20% en holdout strict, utiliser des modèles différents pour génération et évaluation, et enrichir progressivement le dataset avec des cas de production réels.