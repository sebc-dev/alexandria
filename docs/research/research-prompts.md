# Alexandria - Prompts de Recherche

Liste des recherches à effectuer pour compléter le tech-spec Alexandria RAG MCP Server.

---

## Prompt Template (à utiliser avec chaque recherche)

```
# Alexandria RAG MCP Server - Research Agent

## Context
Tu es un expert technique effectuant des recherches pour le projet **Alexandria** - un serveur MCP de recherche sémantique pour Claude Code.

### Stack Technique Validé
- **Runtime**: Bun + TypeScript
- **Database**: PostgreSQL 18 + pgvector 0.8.1 (halfvec 1024D)
- **ORM**: Drizzle + postgres.js
- **Embeddings**: Qwen3-Embedding-0.6B (1024 dims, 32K tokens)
- **Reranker**: bge-reranker-v2-m3 (512 tokens combined)
- **Inference**: RunPod Serverless + Infinity Server
- **MCP SDK**: ^1.22.0 (PAS v2)
- **Validation**: Zod v3 (import depuis "zod/v3")
- **Architecture**: Hexagonale (Domain/Application/Infrastructure)

### Contraintes Clés
- Self-hosted sur serveur personnel (4 cores, 8GB RAM, domaine existant)
- Two-stage retrieval: N=100 candidats → rerank → K=5 résultats
- Cold start RunPod acceptable (10-20s avec FlashBoot)
- Budget cible: ~$25-35/mois pour RunPod

### Fichiers de Référence (dans le projet)
- `docs/research/new/*.md` - Recherches existantes (Qwen3, bge-reranker, RunPod, Infinity)
- `docs/implementation-artifacts/tech-spec-wip.md` - Spec en cours
- `docs/implementation-artifacts/old/tech-spec-alexandria-rag-mcp/` - Ancien spec détaillé

---

## Ta Mission

Recherche approfondie sur le concept suivant:

**{COLLER LE PROMPT DE RECHERCHE ICI}**

---

## Instructions

1. **Utilise WebSearch** pour trouver les informations les plus récentes (nous sommes en janvier 2026)

2. **Structure ta réponse** avec:
   - **TL;DR** (3-5 lignes max)
   - **Specs/Facts** (tableau si applicable)
   - **Options/Approaches** (avec pros/cons)
   - **Recommandation** pour Alexandria (choix + justification)
   - **Code/Config Example** (si pertinent)
   - **Sources** (liens)

3. **Critères d'évaluation** pour chaque option:
   - Compatibilité avec le stack Alexandria
   - Complexité d'implémentation
   - Coût (si applicable)
   - Maintenabilité
   - Sécurité

4. **Output en Français**, termes techniques en anglais

5. **Sauvegarde** le résultat dans `docs/research/new/{slug-du-concept}.md`
```

---

## P0 - Recherches Bloquantes

### Recherche 1: MCP Remote Connection

```
MCP server remote connection patterns for Claude Code

Questions à répondre:
1. Comment Claude Code se connecte-t-il à un serveur MCP distant (pas local)?
2. Quels transports sont supportés? stdio (local only) vs SSE vs HTTP vs WebSocket?
3. Faut-il un tunnel (ngrok, cloudflared) ou une connexion directe HTTPS suffit?
4. Quelle est la configuration côté Claude Code pour pointer vers un serveur distant?
5. Y a-t-il des limitations de sécurité ou de latence documentées?

Contexte Alexandria:
- Le serveur MCP sera hébergé sur un serveur personnel avec un domaine
- Claude Code tourne en local sur ma machine de dev
- Je veux éviter les tunnels si possible (complexité, fiabilité)

Output attendu:
- Architecture de connexion recommandée
- Configuration Claude Code (settings.json ou équivalent)
- Configuration serveur (transport, port, headers)
- Exemple de reverse proxy config si nécessaire
```

---

### Recherche 2: RunPod Infinity Qwen3 Compatibility

```
RunPod worker-infinity-embedding image Qwen3-Embedding-0.6B compatibility

Questions à répondre:
1. L'image officielle `runpod/worker-infinity-embedding:1.1.4` inclut-elle `transformers>=4.51.0`?
2. Si non, quelle version de transformers est incluse?
3. Qwen3-Embedding-0.6B fonctionne-t-il out-of-the-box ou nécessite une image custom?
4. Si custom nécessaire, quel Dockerfile utiliser comme base?
5. L'image officielle supporte-t-elle le multi-model (embedding + reranker)?

Contexte Alexandria:
- Besoin de servir Qwen3-Embedding-0.6B ET bge-reranker-v2-m3
- Préférence pour image officielle (maintenance, updates)
- Si custom, minimiser la complexité du Dockerfile

Output attendu:
- Compatibilité confirmée ou infirmée
- Si custom: Dockerfile complet et testé
- Configuration environment variables pour RunPod
- Estimation cold start avec l'approche choisie
```

---

### Recherche 3: Self-Hosted MCP Deployment

```
Self-hosted MCP server deployment architecture with PostgreSQL

Questions à répondre:
1. Quelle architecture Docker Compose pour PostgreSQL 18 + pgvector + MCP server Bun?
2. Quel reverse proxy recommandé (nginx vs Caddy vs Traefik) pour MCP over HTTPS?
3. Comment configurer SSL/TLS (Let's Encrypt auto-renew)?
4. Le serveur 4 cores / 8GB RAM est-il suffisant pour PostgreSQL + MCP server?
5. Quelles ressources allouer à chaque service (memory limits)?

Contexte Alexandria:
- Serveur personnel accessible via domaine existant
- PostgreSQL 18 avec pgvector 0.8.1
- MCP server en Bun/TypeScript
- Besoin de HTTPS pour connexion Claude Code distante
- Upgrade RAM prévu si nécessaire

Output attendu:
- docker-compose.yml complet et commenté
- Configuration reverse proxy avec SSL
- Sizing recommendations (CPU, RAM per service)
- Health checks et restart policies
```

---

## P1 - Recherches Importantes

### Recherche 4: Chunking Strategy

```
Semantic chunking strategies for technical documentation in RAG systems

Questions à répondre:
1. LLM-based chunking vs RecursiveCharacterTextSplitter: quelle approche pour doc technique?
2. Comment préserver la structure Markdown (headings, code blocks, lists)?
3. Comment extraire automatiquement les headings pour les metadata de chunks?
4. Quelle taille de chunk optimale pour Qwen3-Embedding (32K context disponible)?
5. L'overlap est-il nécessaire avec un modèle 32K tokens?

Contexte Alexandria:
- Documents: Markdown technique, llms.txt, conventions de code
- Mix français/anglais
- Contenu avec beaucoup de code (TypeScript, SQL, config)
- Qwen3-Embedding supporte 32K tokens mais chunks plus petits = meilleure précision

Output attendu:
- Stratégie de chunking recommandée avec justification
- Algorithme ou prompt LLM pour le chunking
- Taille de chunk recommandée (en tokens et caractères)
- Code TypeScript pour l'implémentation
- Gestion des headings et metadata
```

---

### Recherche 5: MCP Authentication

```
MCP server authentication and security patterns for internet-exposed endpoints

Questions à répondre:
1. Comment protéger un serveur MCP exposé sur internet sans système d'auth complet?
2. API key dans headers - supporté par le protocole MCP?
3. Rate limiting au niveau MCP ou reverse proxy?
4. IP whitelisting comme alternative/complément?
5. Stockage sécurisé des secrets (RUNPOD_API_KEY, DATABASE_URL) en self-hosted?

Contexte Alexandria:
- Serveur MCP accessible publiquement (HTTPS)
- Pas de multi-tenancy, usage personnel uniquement
- Besoin de protection contre abus/scan
- Simplicité > complexité (pas d'OAuth, pas de JWT)

Output attendu:
- Pattern d'authentification recommandé
- Configuration côté serveur MCP (middleware)
- Configuration côté client Claude Code
- Rate limiting config (reverse proxy ou app-level)
- Gestion des secrets (env vars, docker secrets, ou autre)
```

---

### Recherche 6: RunPod Architecture Decision

```
RunPod Infinity deployment: single multi-model endpoint vs separate endpoints

Questions à répondre:
1. Performance: un endpoint avec 2 modèles vs 2 endpoints séparés?
2. Cold start: impact sur le temps de chargement?
3. Scaling: peut-on scaler embedding et reranker indépendamment?
4. Coût: différence de pricing entre les deux approches?
5. Network volumes vs baked Docker: quel impact sur cold start?

Contexte Alexandria:
- Modèles: Qwen3-Embedding-0.6B (~2GB) + bge-reranker-v2-m3 (~1GB)
- Usage: ~500 req/jour, bursty (sessions de travail)
- Cold start acceptable: 10-20s
- Budget: ~$25-35/mois

Output attendu:
- Recommandation architecturale avec justification
- Estimation coût pour chaque approche
- Configuration RunPod (workers, timeout, GPU type)
- Trade-offs clairement documentés
```

---

### Recherche 7: PostgreSQL Self-Hosted Config

```
PostgreSQL 18 pgvector production configuration for self-hosted 8GB RAM server

Questions à répondre:
1. Quels paramètres postgresql.conf optimiser pour 8GB RAM avec pgvector?
2. shared_buffers, work_mem, maintenance_work_mem recommandés?
3. Configuration spécifique pour HNSW index (ef_search, maintenance)?
4. Connection pooling nécessaire (PgBouncer) ou postgres.js suffit?
5. Vacuum et analyze: configuration automatique?

Contexte Alexandria:
- PostgreSQL 18 + pgvector 0.8.1
- ~10K-100K chunks à terme
- halfvec(1024) avec index HNSW
- Serveur 4 cores, 8GB RAM (partagé avec MCP server)
- Docker container avec volume persistant

Output attendu:
- postgresql.conf optimisé et commenté
- docker-compose volume et config mount
- Stratégie vacuum/analyze
- Monitoring recommandé (pg_stat_statements, etc.)
- Sizing projection pour croissance
```

---

## P2 - Nice to Have

### Recherche 8: Resilience Patterns

```
RunPod and external API resilience patterns for RAG applications

Questions à répondre:
1. Circuit breaker pattern pour appels RunPod - implémentation TypeScript?
2. Retry avec exponential backoff et jitter - configuration optimale?
3. Fallback strategy si RunPod down: queue les requêtes ou fail fast?
4. Graceful degradation: search sans rerank si reranker timeout?
5. Health check pattern pour détecter RunPod availability?

Contexte Alexandria:
- RunPod Serverless peut avoir des cold starts ou être temporairement indisponible
- MCP server doit rester stable même si RunPod flaky
- Préférence: fail explicite plutôt que timeout silencieux

Output attendu:
- Implémentation circuit breaker en TypeScript
- Configuration retry (attempts, delays, conditions)
- Fallback logic pour search degraded mode
- Error messages clairs pour Claude Code
```

---

### Recherche 9: Local Development Setup

```
Local development environment for Infinity embedding server without RunPod

Questions à répondre:
1. Peut-on run Infinity localement avec Docker (CPU mode)?
2. Performance acceptable pour dev/test sans GPU?
3. Alternative: mock server qui retourne des embeddings aléatoires?
4. ONNX runtime sur CPU comme fallback pour Qwen3?
5. Comment switcher facilement entre local et RunPod (env vars)?

Contexte Alexandria:
- Développement sur laptop sans GPU dédié
- Besoin de tester l'intégration sans coûts RunPod
- Tests automatisés (CI) sans GPU

Output attendu:
- docker-compose.dev.yml avec Infinity local ou mock
- Configuration pour switch local/remote
- Performance expectations en mode CPU
- Setup recommandé pour tests automatisés
```

---

### Recherche 10: PostgreSQL Backup Strategy

```
PostgreSQL backup and recovery strategy for self-hosted Docker deployment

Questions à répondre:
1. pg_dump vs pg_basebackup pour backup de pgvector data?
2. Automatisation avec cron ou script systemd?
3. Où stocker les backups (local, S3, autre)?
4. Point-in-time recovery (PITR) nécessaire ou overkill?
5. Procédure de restore testée?

Contexte Alexandria:
- PostgreSQL 18 en Docker avec volume persistant
- Données: documents + chunks + embeddings (halfvec)
- Criticité: moyenne (rebuild possible mais long)
- Budget: minimal (pas de service managed)

Output attendu:
- Script de backup automatisé
- Cron ou systemd timer configuration
- Procédure de restore documentée
- Retention policy recommandée
- Test de restore à inclure dans les checks
```

---

### Recherche 11: Logging and Monitoring

```
Structured logging and monitoring for self-hosted Bun/TypeScript server

Questions à répondre:
1. Pino: output vers stdout (Docker logs) ou fichier rotatif?
2. Log aggregation self-hosted sans SaaS (Loki? simple grep?)?
3. Metrics basiques: request count, latency, errors - comment collecter?
4. Alerting simple: email ou webhook sur erreurs critiques?
5. Dashboard minimal pour visualiser la santé du système?

Contexte Alexandria:
- Bun runtime avec Pino logger
- Self-hosted, pas de budget pour SaaS monitoring
- Besoin minimal: savoir si le système fonctionne
- Préférence: simplicité > features

Output attendu:
- Configuration Pino optimale
- Solution de log viewing recommandée
- Script ou config pour alerting basique
- Métriques essentielles à tracker
```

---

### Recherche 12: RunPod Cost Optimization

```
RunPod Serverless cost monitoring and optimization strategies

Questions à répondre:
1. Comment tracker les coûts en temps réel (API, dashboard)?
2. Budget alerts disponibles sur RunPod?
3. Idle timeout optimal pour minimiser coûts sans trop de cold starts?
4. FlashBoot: comment mesurer son efficacité?
5. Comparaison coût: active workers vs pure serverless pour ~500 req/jour?

Contexte Alexandria:
- Budget cible: $25-35/mois
- Usage: ~500 req/jour, pattern bursty
- Modèles: Qwen3 + bge-reranker sur L4/A4000

Output attendu:
- Dashboard ou script pour cost tracking
- Configuration optimale (idle timeout, workers)
- Projections de coût selon usage
- Alertes si dépassement budget
```

---

## Checklist de Progression

- [ ] P0-1: MCP Remote Connection
- [ ] P0-2: RunPod Infinity Qwen3
- [ ] P0-3: Self-Hosted Deployment
- [ ] P1-4: Chunking Strategy
- [ ] P1-5: MCP Authentication
- [ ] P1-6: RunPod Architecture
- [ ] P1-7: PostgreSQL Config
- [ ] P2-8: Resilience Patterns
- [ ] P2-9: Local Dev Setup
- [ ] P2-10: PostgreSQL Backup
- [ ] P2-11: Logging/Monitoring
- [ ] P2-12: RunPod Cost Optimization
