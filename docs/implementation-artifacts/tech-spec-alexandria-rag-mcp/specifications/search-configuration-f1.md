# Search Configuration (F1)

| Paramètre | Type | Défaut | Min | Max | Description |
|-----------|------|--------|-----|-----|-------------|
| `threshold` | number | **0.82** | 0.0 | 1.0 | Score minimum de similarité cosinus |
| `limit` | number | 10 | 1 | 60 | Nombre maximum de résultats |
| `tags` | string[] | [] | - | - | Filtrage par tags (OR logic) |

**⚠️ Threshold calibré pour E5:**

Le modèle `multilingual-e5-small` utilise une température de **0.01** pour la loss InfoNCE, ce qui compresse les scores dans une plage étroite. Les textes totalement non-reliés obtiennent des scores de 0.75-0.85.

| Threshold | Usage | Précision/Rappel |
|-----------|-------|------------------|
| 0.80 | Rappel élevé | Plus de résultats, certains moins pertinents |
| **0.82** | **Équilibré (défaut)** | **Compromis recommandé** |
| 0.85 | Précision élevée | Moins de résultats, plus pertinents |
| 0.87-0.90 | Strict | Résultats très pertinents uniquement |

**Configuration HNSW associée:**

- `ef_search = 100` (production standard, configurable par session si besoin)
- `iterative_scan = relaxed_order` (pgvector 0.8+ - améliore le recall avec filtrage)

**Comportement du seuil:**

- Les résultats avec score < threshold sont exclus **après** conversion distance → similarité
- Si aucun résultat ne dépasse le seuil, retourner tableau vide `[]`
- Le score retourné est la similarité cosinus: `1 - distance` (1.0 = identique, 0.0 = orthogonal)

**Distribution des scores E5 (référence):**

| Type de comparaison | Score typique |
|---------------------|---------------|
| Textes totalement non-reliés | 0.75 - 0.85 |
| Textes modérément pertinents | 0.85 - 0.90 |
| Textes très pertinents | 0.90 - 1.0 |
