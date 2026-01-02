# Concurrent Ingestion (F26)

**Comportement lors d'ingestion simultanée même source:**

| Scenario | Comportement |
|----------|--------------|
| 2 clients, même source, upsert=true | Premier à commit gagne, second fait un nouvel upsert (comportement idempotent) |
| 2 clients, même source, upsert=false | Premier gagne, second reçoit DUPLICATE_SOURCE |
| Deadlock | PostgreSQL détecte automatiquement, une transaction est abortée avec DATABASE_ERROR |

**Implémentation:**
- La contrainte UNIQUE sur `source` + transaction garantit l'atomicité
- Pas de lock explicite nécessaire
- Log warning si transaction retry due à conflit
