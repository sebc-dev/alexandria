# Document Content Source (F32)

**Définition:** Le champ `documents.content` contient le contenu **complet du fichier source original**, pas la concaténation des chunks.

| Aspect | Spécification |
|--------|---------------|
| Source | Fichier lu par la CLI/tool (`Bun.file(path).text()`) |
| Stockage | Texte brut complet du fichier |
| Usage | Référence, pas utilisé pour la recherche |
| Chunks | Stockés séparément dans table `chunks` |

**Flow d'ingestion:**
```
1. CLI lit le fichier source → document.content
2. CLI lit le fichier chunks.json → chunks[]
3. Les deux sont stockés dans la même transaction
```
