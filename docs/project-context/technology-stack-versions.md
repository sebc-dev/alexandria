# Technology Stack & Versions

**Core Technologies:**
- **Bun** 1.3.5 - JavaScript runtime ultra-rapide (acquis par Anthropic déc 2025)
- **TypeScript** 5.9.7 - Strict mode activé, dernière branche 5.x stable
- **Hono** 4.11.1 - Framework web TypeScript minimaliste (~12KB)
- **PostgreSQL** 17.7 - Base de données principale
- **pgvector** 0.8.1 - Extension PostgreSQL pour recherche vectorielle
- **Drizzle ORM** 0.36.4 - ORM type-safe avec support pgvector natif

**Key Dependencies:**
- **Zod** 4.2.1 - Validation runtime type-safe (17 déc 2025, 57% bundle plus petit, 3x plus rapide)
- **OpenAI Embeddings API** - text-embedding-3-small ou text-embedding-3-large
- **Claude Haiku 4.5** - LLM reformulation Layer 3 (via sub-agent Claude Code)

**Version Constraints:**
- pgvector 0.8.1 nécessite PostgreSQL 17.7 minimum (abandonne support PostgreSQL 12)
- Zod 4.2.1 a breaking changes depuis 3.x (migration requise si upgrade)
- Drizzle ORM 0.36.4 stable, v1.0 en pré-release (surveiller pour migration future)
- Bun excellente compatibilité future avec Claude Code (acquisition Anthropic)
