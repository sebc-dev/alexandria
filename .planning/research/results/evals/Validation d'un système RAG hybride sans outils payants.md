# Validation d'un système RAG hybride sans outils payants

Un développeur solo disposant de PostgreSQL 17, pgvector, Apache AGE et LangChain4j peut construire une pipeline de validation complète et professionnelle en utilisant exclusivement des outils open-source. La clé réside dans l'utilisation d'Ollama comme moteur LLM local pour les évaluations nécessitant un jugement IA, combinée à des métriques de retrieval pures qui ne requièrent aucun LLM.

## Résumé exécutif : priorités d'implémentation

**Semaine 1-2 : Fondations monitoring.** Déployer VictoriaMetrics + Grafana + pg_stat_statements constitue le socle indispensable. Ces outils consomment **moins de 3 Go RAM** et fournissent une visibilité immédiate sur les performances pgvector. Configurer les métriques Micrometer dans l'application Java expose automatiquement les latences RAG.

**Semaine 3-4 : Métriques de retrieval.** Implémenter Precision@k, Recall@k et MRR en Java pur — ces métriques ne nécessitent aucun LLM et fournissent des mesures déterministes de qualité. Créer un golden dataset de **100-200 paires question-réponse** manuellement curatées sur la documentation technique.

**Semaine 5-6 : Évaluation embeddings et graphe.** Utiliser la bibliothèque SMILE (Java natif) pour le silhouette score et la visualisation UMAP. Implémenter les requêtes Cypher de validation structurelle du graphe Apache AGE : détection d'orphelins, doublons, et métriques de connectivité.

**Semaine 7-8 : Évaluation LLM-as-judge.** Installer Ollama avec Mistral 7B ou Llama 3.1 8B. Le framework **Quarkus LangChain4j Testing** offre une intégration Java native avec support Ollama pour les évaluations AiJudge. Alternative : appeler RAGAS/DeepEval via subprocess Python.

**Recommandations critiques :**
- **VictoriaMetrics** plutôt que Prometheus (consommation RAM **4x inférieure**)
- **Quarkus LangChain4j Scorer** pour rester en écosystème Java
- **NetworkX** via export CSV pour l'analyse de composantes connexes (absent d'AGE)
- Éviter les modèles LLM < 7B paramètres pour le jugement qualité

---

## Axe 1 : Évaluation des embeddings all-MiniLM-L6-v2

### Concepts clés à comprendre

Le modèle all-MiniLM-L6-v2 produit des vecteurs de **384 dimensions** optimisés pour la similarité cosinus. L'évaluation intrinsèque sans ground truth repose sur des métriques de clustering qui mesurent la cohérence sémantique des regroupements naturels dans l'espace vectoriel. Un silhouette score élevé indique que les documents similaires sont proches et les documents différents sont distants.

Le **drift des embeddings** survient lorsque la distribution des nouveaux documents diverge significativement du corpus initial — phénomène critique pour une documentation technique évolutive.

### Outils recommandés

| Outil | Licence | Compatibilité Java | Maturité |
|-------|---------|-------------------|----------|
| **SMILE** | LGPL-3.0 | Native (Maven) | MATURE |
| **UMAP-learn** | BSD-3 | CLI/REST | MATURE |
| **Evidently AI** | Apache-2.0 | CLI/REST (JSON) | MATURE |
| **scikit-learn** | BSD-3 | CLI | MATURE |

**SMILE** (Statistical Machine Intelligence & Learning Engine) est la solution Java native recommandée. Version 5.x active en 2025, elle implémente K-Means, DBSCAN, t-SNE, UMAP et le calcul de silhouette score directement en Java.

### Workflow recommandé

Exporter périodiquement les embeddings depuis pgvector vers un fichier NumPy ou CSV. Exécuter un script Python UMAP pour la visualisation 2D avec `metric='cosine'`. Calculer le silhouette score après clustering K-Means avec différentes valeurs de k (3-10 clusters typiques pour documentation technique). Stocker les métriques baseline et configurer Evidently AI pour détecter le drift via la méthode **Domain Classifier** (ROC-AUC > 0.55 indique un drift significatif).

### Métriques et seuils indicatifs

| Métrique | Mauvais | Acceptable | Bon |
|----------|---------|------------|-----|
| Silhouette score (cosinus) | < 0.1 | 0.1-0.3 | > 0.3 |
| Davies-Bouldin Index | > 2.0 | 1.0-2.0 | < 1.0 |
| Drift ROC-AUC | > 0.7 | 0.55-0.7 | < 0.55 |

### Pièges à éviter

Ne pas utiliser la distance euclidienne pour les embeddings textuels — toujours spécifier `metric='cosine'`. Le silhouette score a une complexité O(N²) : échantillonner si > 10 000 documents. UMAP préserve mieux la structure globale que t-SNE pour l'interprétation des clusters documentaires.

---

## Axe 2 : Validation performance pgvector HNSW

### Concepts clés à comprendre

L'index HNSW (Hierarchical Navigable Small World) est un graphe de proximité multicouche offrant des recherches approximatives en O(log N). Le paramètre **ef_search** contrôle le compromis recall/latence : valeur plus élevée = meilleur recall mais requêtes plus lentes. Le paramètre **m** définit le nombre de connexions par nœud dans le graphe.

Pour un corpus de quelques centaines de documents, l'index HNSW atteint naturellement un recall élevé car le graphe reste compact.

### Outils recommandés

| Outil | Licence | Usage | Maturité |
|-------|---------|-------|----------|
| **pg_stat_statements** | PostgreSQL | Monitoring requêtes | MATURE |
| **pgbench** | PostgreSQL | Benchmark QPS | MATURE |
| **EXPLAIN ANALYZE** | PostgreSQL | Profiling requêtes | MATURE |
| **ann-benchmarks** | MIT | Benchmarks standardisés | MATURE |

Tous ces outils sont intégrés à PostgreSQL ou open-source, aucune dépendance externe.

### Workflow recommandé

Activer pg_stat_statements dans postgresql.conf avec `track_io_timing = on`. Créer une table benchmark_results pour collecter les latences via `clock_timestamp()`. Mesurer le recall@10 en comparant les résultats avec index (`SET hnsw.ef_search = X`) contre les résultats exacts (`SET enable_indexscan = off`). Tester ef_search = 40, 100, 200 pour tracer la courbe recall/latence. Utiliser pgbench avec un script custom pour mesurer le QPS sous charge.

### Métriques et seuils indicatifs

| Scénario | p50 attendu | p99 attendu | Seuil alerte |
|----------|-------------|-------------|--------------|
| Cache froid, sans index | 5-50ms | 50-200ms | > 500ms |
| Cache chaud, HNSW | **0.3-2ms** | 2-10ms | > 20ms |

| ef_search | Recall@10 attendu | Latence relative |
|-----------|-------------------|------------------|
| 40 (défaut) | ~95-98% | 1x |
| 100 | ~98-99% | 2x |
| 200 | ~99%+ | 4x |

### Pièges à éviter

L'index HNSW nécessite obligatoirement `ORDER BY ... LIMIT k` — sans LIMIT, PostgreSQL effectue un scan séquentiel. Vérifier que l'opérateur distance correspond à l'index créé : `vector_cosine_ops` requiert `<=>`, pas `<->` (distance L2). Chauffer le cache avec `pg_prewarm('items_embedding_idx')` avant les benchmarks pour des résultats représentatifs.

---

## Axe 3 : Validation intégrité knowledge graph Apache AGE

### Concepts clés à comprendre

Apache AGE implémente le langage Cypher sur PostgreSQL, stockant nœuds et arêtes dans des tables internes. Contrairement à Neo4j, AGE **ne possède pas d'algorithmes de graphe intégrés** (pas de WCC, PageRank natif). L'analyse de composantes connexes nécessite un export vers NetworkX ou igraph.

Pour un graphe de documentation technique, les métriques pertinentes sont : taux de concepts réutilisés (> 1 document référençant), couverture des propriétés, et ratio nœuds orphelins.

### Outils recommandés

| Outil | Type | Licence | Maturité |
|-------|------|---------|----------|
| **Requêtes Cypher natives** | Validation structurelle | Apache-2.0 | MATURE |
| **AGE Viewer** | Visualisation | Apache-2.0 | MATURE |
| **NetworkX** | Analyse algorithmique | BSD-3 | MATURE |
| **Gephi** | Visualisation avancée | GPL-3.0 | MATURE |

### Workflow recommandé

**Quotidien (Cypher)** : Exécuter les requêtes de détection d'orphelins (`WHERE NOT (n)-[]-()`), doublons (`WITH ... count(*) > 1`), et statistiques basiques.

**Hebdomadaire (Python)** : Exporter nœuds et arêtes via `COPY ... TO CSV`, charger dans NetworkX, calculer `weakly_connected_components()` et `density()`.

**Mensuel (Gephi)** : Export GraphML pour inspection visuelle, identification des clusters documentaires, détection des gaps.

### Métriques et seuils indicatifs

| Métrique | Formule/Requête | Seuil cible |
|----------|-----------------|-------------|
| Nœuds orphelins | `MATCH (n) WHERE NOT (n)-[]-() RETURN count(n)` | < 5% |
| Taux réutilisation concepts | Concepts avec > 1 doc entrant / Total | > 30% |
| Couverture propriétés | Nœuds avec description / Total | > 80% |
| Densité graphe | \|E\| / (\|V\| × (\|V\|-1)) | 0.01-0.1 typique |

### Pièges à éviter

AGE ne supporte pas `apoc.algo.wcc()` ni les procédures Neo4j — ne pas chercher ces fonctions. L'export vers NetworkX est obligatoire pour l'analyse de connectivité. Attention au dialecte Cypher : certaines fonctions Neo4j récentes ne sont pas implémentées dans AGE 1.6.

---

## Axe 4 : Évaluation end-to-end RAG sans API payante

### Concepts clés à comprendre

L'évaluation RAG se divise en deux niveaux : **retrieval** (qualité des documents récupérés) et **generation** (qualité de la réponse produite). Les métriques de retrieval (Precision@k, Recall@k, MRR, NDCG) sont déterministes et ne nécessitent aucun LLM. Les métriques de generation (faithfulness, relevance) utilisent un LLM-as-judge.

**Tous les frameworks majeurs (RAGAS, DeepEval, TruLens, Phoenix) supportent Ollama** comme backend LLM gratuit.

### Outils recommandés

| Framework | Licence | Support Ollama | Java natif | Maturité |
|-----------|---------|----------------|------------|----------|
| **Quarkus LangChain4j Scorer** | Apache-2.0 | ✅ | ✅ Natif | EMERGING |
| **RAGAS** | Apache-2.0 | ✅ | CLI/REST | MATURE |
| **DeepEval** | MIT | ✅ Natif | CLI/REST | MATURE |
| **TruLens** | MIT | ✅ via LiteLLM | CLI/REST | MATURE |
| **Phoenix (Arize)** | ELv2 | ✅ via LiteLLM | REST/OTEL | MATURE |

**Quarkus LangChain4j Testing** est la seule solution Java native avec intégration JUnit 5 et support direct d'Ollama comme juge IA.

### Workflow recommandé

**Phase 1 — Métriques retrieval pures** : Implémenter Precision@k, Recall@k, MRR en Java. Créer un golden dataset de 100-200 QA pairs sur la documentation. Évaluer sans aucun coût LLM.

**Phase 2 — Génération de dataset synthétique** : Utiliser RAGAS TestsetGenerator avec Ollama pour générer des questions additionnelles. Filtrer manuellement pour créer un dataset "silver".

**Phase 3 — Évaluation LLM-as-judge** : Configurer Quarkus LangChain4j avec `AiJudgeStrategy` pointant vers `http://localhost:11434/v1`. Évaluer faithfulness et answer relevancy.

### Métriques et seuils indicatifs

| Métrique | Type | Seuil acceptable |
|----------|------|------------------|
| Precision@5 | Retrieval | > 0.7 |
| Recall@10 | Retrieval | > 0.8 |
| MRR | Retrieval | > 0.6 |
| Faithfulness (RAGAS) | Generation | > 0.8 |
| Answer Relevancy | Generation | > 0.7 |

### Pièges à éviter

Les modèles < 7B paramètres produisent des jugements instables — utiliser Mistral 7B ou Llama 3.1 8B minimum. RAGAS et DeepEval ont des features cloud (dashboards, monitoring) qui sont payantes, mais l'évaluation locale est 100% gratuite. Attention aux erreurs de parsing JSON avec les petits modèles.

---

## Axe 5 : Monitoring et observabilité self-hosted

### Concepts clés à comprendre

L'observabilité comprend trois piliers : **métriques** (valeurs numériques temporelles), **traces** (suivi des requêtes distribuées), et **logs** (événements textuels). Pour un RAG hybride, les métriques critiques sont la latence E2E, le temps de retrieval vectoriel, le temps de traversée graphe, et les scores de similarité.

**VictoriaMetrics** offre une alternative à Prometheus avec une consommation RAM **4x inférieure** (4.3 Go stable vs 6-23 Go pour Prometheus).

### Outils recommandés — Stack minimale

| Composant | Outil | RAM | Licence | Maturité |
|-----------|-------|-----|---------|----------|
| Métriques PostgreSQL | **pgwatch** | ~500MB | BSD-3 | MATURE |
| Métriques Java | **Micrometer** | ~50MB | Apache-2.0 | MATURE |
| Stockage métriques | **VictoriaMetrics** | ~1-2GB | Apache-2.0 | MATURE |
| Visualisation | **Grafana** | ~300MB | AGPL-3.0 | MATURE |
| Logging | **Loki + Promtail** | ~500MB | AGPL-3.0 | MATURE |
| Alerting | **vmalert** (inclus VM) | Inclus | Apache-2.0 | MATURE |

**Total RAM stack minimale : ~3-4 Go** — compatible avec le serveur 24 Go.

### Workflow recommandé

Déployer la stack via Docker Compose (voir configuration ci-dessous). Configurer Micrometer dans l'application Java pour exposer `/actuator/prometheus`. Créer des dashboards Grafana pour : latence RAG p50/p95/p99, temps requêtes pgvector, requêtes Cypher, taux d'erreur. Configurer vmalert pour alerter si p95 > 5 secondes ou taux erreur > 5%.

### Métriques RAG spécifiques à tracker

| Catégorie | Métriques | Outil source |
|-----------|-----------|--------------|
| Latence | E2E, retrieval, generation, embedding | Micrometer Timer |
| Retrieval | Docs récupérés, scores similarité | Custom Gauge |
| pgvector | Temps requête, utilisation index | pg_stat_statements |
| Graphe | Temps traversée, nœuds visités | Custom Counter |
| Erreurs | Taux échec LLM, timeout retrieval | Micrometer Counter |

### Pièges à éviter

Prometheus peut consommer jusqu'à 23 Go RAM avec des pics — préférer VictoriaMetrics. Loki n'indexe que les labels, pas le contenu des logs — adapter les requêtes LogQL. Jaeger all-in-one avec stockage Badger suffit pour un développeur solo — éviter Elasticsearch pour les traces.

---

## Matrice de décision : quel outil pour quel besoin

| Besoin | Outil recommandé | Alternative | Contrainte |
|--------|------------------|-------------|------------|
| **Silhouette score Java** | SMILE | Export → scikit-learn | LGPL-3.0 |
| **Visualisation embeddings** | UMAP-learn (Python CLI) | SMILE UMAP | Nécessite Python |
| **Drift detection** | Evidently AI | Manuel (centroid) | Python CLI |
| **Benchmark pgvector** | pgbench + pg_stat_statements | ann-benchmarks | Intégré PostgreSQL |
| **Recall@k pgvector** | Requête SQL manuelle | ann-benchmarks | Aucune |
| **Orphelins graphe** | Cypher natif AGE | NetworkX | Aucune |
| **WCC graphe** | NetworkX (export CSV) | igraph | Export obligatoire |
| **Visualisation graphe** | AGE Viewer | Gephi | Node.js requis |
| **Évaluation retrieval** | Java custom (P@k, MRR) | pytrec_eval | Aucune |
| **Évaluation generation** | Quarkus LangChain4j Scorer | RAGAS + Ollama | Ollama requis |
| **LLM-as-judge** | Ollama (Mistral 7B) | Llama 3.1 8B | 8 Go RAM GPU |
| **Métriques stockage** | VictoriaMetrics | Prometheus | RAM limitée |
| **Dashboard** | Grafana | - | Standard |
| **Tracing** | Jaeger | Zipkin | OTEL compatible |
| **Logging** | Loki | Elasticsearch | RAM limitée |
| **Alerting** | vmalert / Alertmanager | - | Standard |

---

## Workflow global intégré sur 8 semaines

### Phase 1 : Infrastructure monitoring (Semaines 1-2)

Déployer Docker Compose avec VictoriaMetrics, Grafana, Loki, Jaeger. Activer pg_stat_statements sur PostgreSQL 17. Configurer Micrometer dans l'application Java avec registry Prometheus. Créer dashboard Grafana basique : latence requêtes, QPS, erreurs. Configurer alertes : p95 > 5s, taux erreur > 5%.

**Livrable** : Dashboard opérationnel avec visibilité temps réel.

### Phase 2 : Benchmark pgvector (Semaines 3-4)

Créer table benchmark_results et fonction measure_recall(). Exécuter benchmarks avec ef_search = 40, 100, 200. Documenter courbe recall/latence. Configurer index HNSW avec paramètres optimaux. Intégrer métriques pgvector dans Grafana.

**Livrable** : Configuration HNSW optimisée, baseline performance documentée.

### Phase 3 : Validation knowledge graph (Semaine 5)

Implémenter requêtes Cypher quotidiennes (orphelins, doublons). Créer script Python export → NetworkX pour WCC. Générer rapport hebdomadaire de qualité graphe. Documenter seuils acceptables.

**Livrable** : Pipeline validation graphe automatisée.

### Phase 4 : Évaluation embeddings (Semaine 6)

Configurer SMILE pour silhouette score Java natif. Créer script Python UMAP pour visualisation. Établir baseline drift avec Evidently. Intégrer métriques dans Grafana.

**Livrable** : Dashboard qualité embeddings avec alertes drift.

### Phase 5 : Golden dataset et métriques retrieval (Semaine 7)

Créer 100-200 QA pairs manuellement sur documentation. Implémenter Precision@k, Recall@k, MRR en Java. Exécuter évaluation baseline. Documenter scores cibles.

**Livrable** : Golden dataset versionné, scores retrieval baseline.

### Phase 6 : Évaluation LLM-as-judge (Semaine 8)

Installer Ollama avec Mistral 7B. Configurer Quarkus LangChain4j Testing avec AiJudgeStrategy. Exécuter évaluation faithfulness et answer relevancy. Intégrer dans pipeline CI.

**Livrable** : Pipeline évaluation complète automatisée.

---

## Limitations et zones d'incertitude identifiées

### Confirmé et fiable

- **pgvector HNSW** : méthodes de benchmark bien documentées, métriques standardisées
- **VictoriaMetrics** : consommation RAM documentée, compatible Prometheus
- **RAGAS/DeepEval + Ollama** : support confirmé dans documentation officielle
- **NetworkX** : solution mature pour analyse graphe post-export
- **SMILE** : bibliothèque Java mature (6.3k stars GitHub)

### À vérifier selon version exacte

- **[À VÉRIFIER]** Quarkus LangChain4j Scorer : framework emerging, API peut évoluer
- **[À VÉRIFIER]** Apache AGE 1.6.0 : certaines fonctions Cypher peuvent différer de Neo4j
- **[À VÉRIFIER]** Evidently AI : version récente, tester compatibilité embeddings 384 dims

### Règles empiriques (non sourcées)

- **[RÈGLE EMPIRIQUE]** Silhouette > 0.3 = bon pour documentation technique
- **[RÈGLE EMPIRIQUE]** 100-200 QA pairs suffisent pour évaluation solo developer
- **[RÈGLE EMPIRIQUE]** Modèles < 7B produisent des jugements instables

### Limitations structurelles

La qualité de l'évaluation LLM-as-judge dépend directement de la capacité du modèle local. Mistral 7B offre un bon compromis qualité/ressources mais reste inférieur à GPT-4 pour les jugements nuancés. Pour une évaluation critique, privilégier les métriques de retrieval déterministes.

Apache AGE ne possède pas d'algorithmes de graphe intégrés — l'export vers Python est obligatoire pour toute analyse de connectivité avancée. Cette limitation architecturale ne sera probablement pas résolue à court terme.

Le monitoring self-hosted nécessite une maintenance régulière (mises à jour, rétention données, backups). Prévoir 2-4 heures/mois pour un développeur solo.