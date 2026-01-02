# Search Configuration (F1)

| Paramètre | Type | Défaut | Description |
|-----------|------|--------|-------------|
| `threshold` | number | 0.5 | Score minimum de similarité cosinus (0.0 - 1.0) |
| `limit` | number | 10 | Nombre maximum de résultats |
| `tags` | string[] | [] | Filtrage par tags (OR logic) |

**Comportement du seuil:**
- Les résultats avec score < threshold sont exclus
- Si aucun résultat ne dépasse le seuil, retourner tableau vide `[]`
- Le score retourné est la similarité cosinus convertie (1.0 = identique, 0.0 = orthogonal)
