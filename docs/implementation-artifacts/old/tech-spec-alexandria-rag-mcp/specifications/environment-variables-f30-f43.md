# Environment Variables (F30, F43)

**Variables d'environnement:**

| Variable | Défaut | Requis | Description |
|----------|--------|--------|-------------|
| `DATABASE_URL` | - | **Oui** | Connection string PostgreSQL (F43) |
| `LOG_LEVEL` | `info` | Non | Niveau de log (debug/info/warn/error) |
| `HF_HOME` | `~/.cache/huggingface` | Non | Répertoire cache Hugging Face |
| `TRANSFORMERS_CACHE` | `$HF_HOME/hub` | Non | Cache spécifique transformers |

**Format DATABASE_URL:**
```
postgresql://user:password@host:port/database
```

**Exemple .env:**
```bash
DATABASE_URL=postgresql://alexandria:secret@localhost:5432/alexandria
LOG_LEVEL=debug
HF_HOME=/app/.cache/huggingface
```

**Docker considerations:**
```yaml
# docker-compose.yml
services:
  alexandria:
    volumes:
      - hf-cache:/root/.cache/huggingface
volumes:
  hf-cache:
```

**Permissions:** Le répertoire cache doit être writable par le process.
