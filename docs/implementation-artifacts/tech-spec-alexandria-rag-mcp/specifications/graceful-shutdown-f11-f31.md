# Graceful Shutdown (F11, F31)

**Séquence complète avec gestion du model loading:**

| Phase | Timeout | Action |
|-------|---------|--------|
| 1. Stop accepting | 0s | Refuser nouvelles connexions MCP |
| 2. Cancel model loading | 0s | Si model en loading, cancel immédiat (pas d'attente 60s) |
| 3. Drain requests | 30s | Attendre fin des requêtes en cours |
| 4. Close DB pool | 5s | Fermer connexions PostgreSQL |
| 5. Force exit | 0s | process.exit(0) |

**Gestion du model loading pendant shutdown (F31):**
- Si model est en état "loading" au moment du SIGTERM, cancel immédiatement
- Ne pas attendre les 60s du timeout de loading
- Les requêtes en attente du model reçoivent MODEL_LOAD_TIMEOUT

```typescript
process.on('SIGTERM', async () => {
  server.stopAccepting();
  embedder.cancelLoading();  // Cancel immédiat si loading
  await server.drain(30_000);
  await db.close(5_000);
  process.exit(0);
});
```
