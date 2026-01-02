# Transaction Isolation (F18)

| Aspect | Spécification |
|--------|---------------|
| Isolation Level | **READ COMMITTED** (défaut PostgreSQL) |
| Rationale | Bon compromis perf/consistance, pas de phantom reads critiques pour ce use case |
| Ingestion | Transaction unique englobant document + tous les chunks |
| Upsert | DELETE + INSERT dans même transaction |
| Search | Pas de transaction (read-only, snapshot suffisant) |

**Configuration:**
```typescript
// postgres.js configuration
const sql = postgres(DATABASE_URL, {
  // READ COMMITTED est le défaut, pas besoin de le spécifier
  max: 10,  // pool size
});
```
