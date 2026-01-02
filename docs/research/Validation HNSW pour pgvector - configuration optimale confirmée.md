# Validation HNSW pour pgvector : configuration optimale confirmée

Votre configuration HNSW (m=16, ef_construction=64, ef_search=40) correspond exactement aux **valeurs par défaut de pgvector**, validées par le mainteneur Andrew Kane comme appropriées pour les petits volumes. Pour 1K-10K vecteurs à 384 dimensions, ces paramètres offrent un excellent équilibre qualité/performance sans surcharge inutile. La persistance via ALTER DATABASE est confirmée fiable.

---

## Validation du paramètre m=16 : optimal pour votre cas

Le paramètre **m** définit le nombre maximal de connexions bidirectionnelles par nœud dans le graphe HNSW. La valeur **16** est le défaut pgvector et se situe dans la plage recommandée **5-48** du paper original HNSW.

**Impact mémoire avec m=16 pour 10K vecteurs à 384 dimensions :**

| Composant | Formule | Estimation |
|-----------|---------|------------|
| Stockage vecteurs | 10 000 × 384 × 4 bytes | ~15 Mo |
| Overhead graphe HNSW | 10 000 × (16 × 8) bytes | ~1,3 Mo |
| **Total index** | — | **~18-20 Mo** |

**Comparaison m=16 vs m=32 :**

| Métrique | m=16 | m=32 | Différence |
|----------|------|------|------------|
| Recall typique | 95-98% | 97-99% | +1-2% marginal |
| Temps construction | Baseline | +40-60% | Significatif |
| Taille index | ~20 Mo | ~25 Mo | +25% |
| Latence requête | ~0.5ms | ~0.7ms | +40% |

Pour un volume <10K vecteurs, **m=16 est parfaitement adapté**. Le mainteneur pgvector recommande explicitement : *"I'd recommend using the default m for faster build times unless you're seeing poor recall with a higher value of ef_construction."* Augmenter à m=32 apporterait un gain de recall négligeable (+1-2%) pour une surcharge non justifiée. Avec 384 dimensions (considéré "modéré"), m=12-16 est la plage optimale selon les benchmarks hnswlib.

---

## Validation ef_construction=64 : valeur suffisante

Le paramètre **ef_construction** contrôle la taille de la liste de candidats lors de la construction du graphe. La valeur **64** est le défaut pgvector et respecte la règle **ef_construction ≥ 2×m** (minimum théorique : 32).

**Impact sur la construction de l'index :**

| ef_construction | Temps relatif | Qualité index | Recall@10 |
|-----------------|---------------|---------------|-----------|
| 64 (défaut) | 1× | Baseline | ~95-97% |
| 128 | ~1.5-2× | +3% qualité | ~97-98% |
| 256 | ~3-4× | +1.3% additionnel | ~98-99% |

Les benchmarks Jonathan Katz (contributeur pgvector) sur mnist-784 montrent :
- Avec m=16, ef_construction=40 : build en **1.02 min** pour 60K vecteurs
- Avec m=16, ef_construction=60 : build en **0.87 min** pour 60K vecteurs

**Pour 10K vecteurs, le temps de construction sera de quelques secondes**, rendant toute optimisation de ce paramètre académique. Le paper HNSW original précise : *"Further increase of efConstruction leads to little extra performance but in exchange of significantly longer construction time."*

**Recommandation** : Conserver **ef_construction=64**. Si vous observez un recall inférieur à 90%, augmentez à 128 lors de la reconstruction de l'index.

---

## Validation ef_search=40 : ajustable selon le use case

Le paramètre **ef_search** contrôle la taille de la liste dynamique de candidats lors des recherches. La valeur **40** est le défaut pgvector.

**Impact sur recall et latence :**

| ef_search | Recall@10 estimé | Latence relative | Use case |
|-----------|------------------|------------------|----------|
| 40 (défaut) | ~93-95% | 1× | Standard, LIMIT ≤40 |
| 60 | ~95-97% | ~1.3× | Meilleur équilibre |
| 100 | ~97-98% | ~1.8× | Haute qualité |
| 200 | ~99%+ | ~3× | Recall maximal |

**Contrainte critique** : ef_search doit être **≥ LIMIT** dans vos requêtes. Avec ef_search=40, une requête `LIMIT 50` ne retournera que 40 résultats maximum.

**Recommandation pour recherche sémantique de qualité** : Pour une base documentaire où la pertinence est prioritaire, **augmentez ef_search à 60-100**. La documentation Neon et AWS recommande ef_search≥100 pour les applications nécessitant un recall élevé.

```sql
-- Configuration recommandée pour qualité
ALTER DATABASE alexandria SET hnsw.ef_search = 60;

-- Ou pour des requêtes spécifiques nécessitant haute précision
SET LOCAL hnsw.ef_search = 100;
```

---

## Configuration ALTER DATABASE : persistance confirmée

**Oui, ALTER DATABASE ... SET persiste après redémarrage PostgreSQL.** Cette information est stockée dans le catalogue système `pg_db_role_setting`, partagé au niveau du cluster.

**Hiérarchie de précédence des paramètres (priorité décroissante) :**

| Priorité | Source | Persistance |
|----------|--------|-------------|
| 1 (haute) | SET session | ❌ Session uniquement |
| 2 | ALTER ROLE ... SET | ✅ Persistant |
| 3 | **ALTER DATABASE ... SET** | ✅ **Persistant** |
| 4 | postgresql.auto.conf | ✅ Après reload |
| 5 | postgresql.conf | ✅ Après reload |
| 6 | Défauts compilés | — |

**Vérification de la configuration :**
```sql
-- Voir tous les paramètres ALTER DATABASE/ROLE
SELECT * FROM pg_db_role_setting;

-- Ou via psql
\drds

-- Vérifier la valeur effective
SHOW hnsw.ef_search;
```

**Limitations et risques :**
- Les paramètres s'appliquent uniquement aux **nouvelles sessions** (les connexions existantes ne voient pas le changement)
- L'extension pgvector doit être chargée pour que le paramètre soit validé (sinon stocké comme "placeholder")
- Sur RDS/Cloud SQL, certaines restrictions de permissions peuvent s'appliquer

**ALTER DATABASE vs alternatives :**

| Approche | Avantage | Inconvénient |
|----------|----------|--------------|
| ALTER DATABASE | Persistant, par DB | Nouvelles sessions uniquement |
| postgresql.conf | Cluster-wide | Nécessite reload, moins flexible |
| SET session | Immédiat | Non persistant |

**Votre approche ALTER DATABASE est la meilleure** pour un paramètre spécifique à une base de données comme hnsw.ef_search.

---

## Recommandations finales et configuration optimisée

### Configuration validée (votre setup actuel)

```sql
-- Index HNSW - VALIDÉ ✓
CREATE INDEX ON documents 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);

-- Persistance ef_search - VALIDÉ ✓
ALTER DATABASE alexandria SET hnsw.ef_search = 40;
```

### Configuration optimisée recommandée

Pour une recherche sémantique de qualité sur base documentaire, je recommande un léger ajustement de ef_search :

```sql
-- Index HNSW (conserver les paramètres actuels)
CREATE INDEX CONCURRENTLY ON documents 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);

-- Augmenter ef_search pour meilleur recall
ALTER DATABASE alexandria SET hnsw.ef_search = 60;

-- Memory pour construction (optionnel, utile si scale-up futur)
ALTER DATABASE alexandria SET maintenance_work_mem = '256MB';
```

### Tableau récapitulatif de validation

| Paramètre | Votre valeur | Défaut pgvector | Statut | Recommandation |
|-----------|--------------|-----------------|--------|----------------|
| **m** | 16 | 16 | ✅ Optimal | Conserver |
| **ef_construction** | 64 | 64 | ✅ Optimal | Conserver |
| **ef_search** | 40 | 40 | ⚠️ Acceptable | Augmenter à 60 |
| **ALTER DATABASE** | Oui | — | ✅ Correct | Approche recommandée |

### Estimations de performance pour votre setup

Pour **10K vecteurs à 384 dimensions avec multilingual-e5-small** :

| Métrique | Estimation |
|----------|------------|
| Taille index HNSW | ~18-25 Mo |
| Temps construction index | 2-5 secondes |
| Latence requête (ef_search=60) | <1 ms |
| Recall@10 attendu | 95-97% |
| Mémoire RAM requise | ~50 Mo (index + cache) |

### Note sur l'alternative sans index

Pour <10K vecteurs, la documentation Neon indique qu'un **scan séquentiel** prend ~36ms avec 100% de recall. Si vous privilégiez l'exactitude absolue et n'avez pas de contraintes de latence strictes, l'index HNSW reste optionnel à cette échelle. Cependant, pour une UX réactive (<10ms), l'index est justifié.

---

## Sources officielles consultées

- **pgvector GitHub** (README, CHANGELOG, Issues #731, #735, #769)
- **PostgreSQL 18 Documentation** (ALTER DATABASE, pg_db_role_setting, GUC system)
- **Jonathan Katz** (mainteneur pgvector) - benchmarks HNSW et recommandations
- **Andrew Kane** (mainteneur pgvector) - guidance GitHub Issue #731
- **AWS Database Blog** - optimisation pgvector HNSW
- **Neon Documentation** - guide optimisation recherche vectorielle
- **hnswlib** (implémentation de référence) - paramètres algorithme
- **Paper HNSW original** - plages recommandées pour m et ef_construction