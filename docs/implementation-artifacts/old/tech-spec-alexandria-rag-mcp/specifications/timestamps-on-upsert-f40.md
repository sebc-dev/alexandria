# Timestamps on Upsert (F40)

| Champ | Comportement Upsert |
|-------|---------------------|
| `created_at` | **Nouvelle valeur** (NOW()) - l'ancien timestamp est perdu |
| `updated_at` | **Nouvelle valeur** (NOW()) |

**Rationale:** L'upsert fait DELETE + INSERT, donc c'est techniquement un nouveau document. Pour MVP, on accepte la perte de `created_at` original. Une évolution future pourrait préserver le timestamp via SELECT avant DELETE.
