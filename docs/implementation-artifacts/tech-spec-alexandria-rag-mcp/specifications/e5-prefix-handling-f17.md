# E5 Prefix Handling (F17)

**Format des prefixes:**

| Type | Prefix | Format exact |
|------|--------|--------------|
| Query (recherche) | `query: ` | "query: " + content (espace après colon) |
| Passage (ingestion) | `passage: ` | "passage: " + content (espace après colon) |

**Règles de gestion:**

1. **Ajout automatique**: Le système ajoute TOUJOURS le prefix approprié
2. **Content existant avec prefix**: Si content commence par "query:" ou "passage:", le prefix est quand même ajouté (double prefix = bug dans les données source, pas notre problème)
3. **Whitespace**: Un seul espace après le colon, pas de trim du content
4. **Token limit**: Le prefix compte dans les ~512 tokens max de E5

**Exemple:**
```typescript
// Input: "Comment configurer TypeScript?"
// Query embedding: "query: Comment configurer TypeScript?"

// Input: "passage: déjà préfixé par erreur"
// Passage embedding: "passage: passage: déjà préfixé par erreur"
// (le double prefix est une erreur de l'utilisateur, pas du système)
```
