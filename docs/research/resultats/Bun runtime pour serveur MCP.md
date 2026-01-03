# Bun runtime pour serveur MCP : une évaluation technique mitigée

L'analyse approfondie révèle que **Bun n'est pas recommandé pour un serveur MCP production utilisant le transport stdio**, malgré ses performances impressionnantes. Un bug critique de blocage stdin au-delà de 16KB, combiné à des memory leaks documentés sur serveurs long-running, pose des risques significatifs. Node.js reste le choix le plus sûr pour ce cas d'usage spécifique. Cependant, Bun peut être envisagé pour le développement local et le prototypage.

**Fait marquant** : Anthropic a acquis Bun le 2 décembre 2025, ce qui pourrait améliorer la compatibilité MCP à terme, mais les problèmes actuels persistent dans la version stable.

---

## Version stable et état du projet

Bun **v1.3.5** (17 décembre 2025) représente l'état actuel du runtime. Le projet maintient un rythme de développement soutenu avec 1-2 releases hebdomadaires et compte environ **84 600 étoiles GitHub**. L'acquisition par Anthropic vise à intégrer Bun comme infrastructure pour Claude Code et les outils AI.

Les fonctionnalités majeures récentes incluent l'API SQL unifiée (`Bun.sql`) supportant PostgreSQL/MySQL/SQLite, un client Redis intégré (`Bun.redis`), et le frontend development avec HMR natif. La version 1.3.x apporte également `Bun.Terminal API`, les fake timers pour les tests, et **287 bugs corrigés** dans la seule v1.3.2.

| Version | Date | Points clés |
|---------|------|-------------|
| **1.3.5** | 17 déc. 2025 | Terminal API, V8 C++ APIs |
| 1.3.0 | 10 oct. 2025 | Frontend dev, Bun.sql, Redis |
| 1.2.0 | Jan. 2025 | PostgreSQL natif, S3, nouveau lockfile |

Malgré cette activité, **~4 600 issues restent ouvertes**, dont plusieurs concernant des memory leaks critiques pour les serveurs de production.

---

## Compatibilité des packages critiques

### postgres.js : support officiel confirmé

Le driver postgres.js **supporte officiellement Bun** — le README GitHub mentionne explicitement "The Fastest full featured PostgreSQL client for Node.js, Deno, **Bun** and CloudFlare". L'utilisation est directe sans configuration particulière :

```javascript
import postgres from 'postgres'
const sql = postgres('postgres://...')
```

À noter : Bun propose son propre driver `bun:sql` prétendument **50% plus rapide** que postgres.js, mais son API n'est pas 100% compatible. Pour la stabilité, **postgres.js reste préférable** au driver natif encore immature.

### drizzle-orm : fonctionnel avec précautions

Le support Bun est **officiellement documenté** avec deux modes : `drizzle-orm/bun-sqlite` et `drizzle-orm/bun-sql`. Cependant, un **bug critique de concurrence** affecte Bun 1.2.0+ avec le driver natif :

> "In version 1.2.0, Bun has issues with executing concurrent statements, which may lead to errors if you try to run several queries simultaneously" — Documentation Drizzle

Les erreurs typiques incluent `PostgresError: Failed to read data` avec code `ERR_POSTGRES_UNSUPPORTED_INTEGER_SIZE`. **Workaround recommandé** : utiliser `drizzle-orm/postgres-js` avec postgres.js plutôt que le driver natif Bun.

```javascript
// Configuration recommandée
import { drizzle } from 'drizzle-orm/postgres-js';
import postgres from 'postgres';
const db = drizzle(postgres(process.env.DATABASE_URL));
```

### @modelcontextprotocol/sdk : non-officiel mais fonctionnel

Le SDK MCP d'Anthropic **ne supporte pas officiellement Bun** — le `package.json` spécifie `"engines": { "node": ">=20" }`. Néanmoins, des développeurs rapportent un fonctionnement correct en pratique. La configuration Claude Desktop requiert le chemin complet de bun pour éviter l'erreur `spawn bun ENOENT` :

```json
{
  "mcpServers": {
    "myserver": {
      "command": "/home/user/.bun/bin/bun",
      "args": ["run", "server.ts"]
    }
  }
}
```

La roadmap V2 du SDK (prévue Q1 2026) mentionne Bun comme "non-node environment consideration", suggérant un support futur possible.

---

## Limitations critiques pour serveur MCP

### Le blocage stdin rend le transport stdio risqué

**Issue GitHub #9041** révèle un problème majeur : Bun **bloque indéfiniment** lorsque stdin reçoit ≥16 384 bytes. Pour un serveur MCP traitant des embeddings ou du contexte RAG, les messages JSON peuvent facilement dépasser cette limite.

```javascript
// Workaround partiel - utiliser stream() au lieu de process.stdin
const reader = Bun.stdin.stream().getReader();
```

Ce comportement diffère fondamentalement de Node.js où `process.stdin` gère correctement les payloads volumineux. Pour un serveur MCP stdio, ce bug représente un **risque bloquant**.

### Memory leaks documentés sur serveurs long-running

Plusieurs issues GitHub documentent des fuites mémoire critiques :

| Issue | Contexte | Symptôme |
|-------|----------|----------|
| **#18488** | Streaming fetch (≥1.1.27) | OOM avec Hono.js/Bun.serve |
| **#24216** | Nest.js + MongoDB | Mémoire à 100% |
| **#24118** | MongoDB driver | ~5MB/heure vs ~500KB sur Node |
| **#19930** | Express + Docker | Crash pods 4GB RAM |

Le garbage collector JavaScriptCore de Bun est moins optimisé que V8 pour les workloads serveur prolongés. **Recommandation uptime** : minutes/heures = stable, jours = risqué, semaines = non recommandé.

### WebSocket stable, SSE problématique

Le WebSocket natif de Bun fonctionne bien avec pub/sub intégré. En revanche, **SSE présente des régressions critiques** : la version 1.1.27 provoque des crashes serveur complets sur Linux (Issue #13811). Si votre architecture MCP envisage un fallback HTTP streaming, ce point mérite attention.

### Extra stdio file descriptors non supportés

Issue #4670 : Bun ne supporte pas les fd > 2 dans `Bun.spawn()`. Si votre serveur MCP nécessite une communication IPC complexe au-delà de stdin/stdout/stderr, cette limitation est bloquante.

---

## Configuration production Linux optimale

### Service systemd recommandé

```ini
[Unit]
Description=MCP Server Bun
After=network.target postgresql.service

[Service]
Type=simple
User=mcp
WorkingDirectory=/opt/mcp-server
ExecStart=/home/mcp/.bun/bin/bun run index.ts
Restart=always
RestartSec=10
Environment=NODE_ENV=production
Environment=BUN_ENV=production
StandardOutput=journal
StandardError=journal
KillSignal=SIGTERM
TimeoutStopSec=30

# Sécurité
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=read-only

[Install]
WantedBy=multi-user.target
```

Le `RestartSec=10` compense les potentiels memory leaks en assurant un redémarrage automatique. Logs accessibles via `journalctl -u mcp-server -f`.

### Optimisations mémoire pour 24GB RAM

Bun utilise deux heaps distincts : JavaScriptCore pour le runtime JS et mimalloc pour les allocations natives. Contrairement à Node.js, **aucune variable d'environnement ne configure explicitement la limite heap** — Bun utilise ~80% de la RAM disponible et ajuste le GC agressivement.

```typescript
// Monitoring programmatique
import { heapStats, memoryUsage } from "bun:jsc";
console.log({ heapSize: heapStats().heapSize, peak: memoryUsage().peak });
```

**Éviter le flag `--smol`** sur un serveur bien doté — il réduit les performances au profit d'une empreinte mémoire minimale, optimisé pour environnements contraints.

### Compilation vs interprétation

Pour la production, la compilation en exécutable standalone offre des avantages :

```bash
bun build --compile --minify --bytecode ./src/index.ts --outfile mcp-server
```

| Aspect | Compilé | Interprété |
|--------|---------|------------|
| Démarrage | **Plus rapide** (pas de transpilation) | Overhead initial |
| Déploiement | Un fichier ~50-100MB | node_modules requis |
| Debug | Sourcemaps possibles | Direct |
| Mémoire | **Réduite** | Cache transpilation |

Le flag `--bytecode` déplace le parsing vers le build-time, améliorant le cold start.

---

## TypeScript strict mode : support complet

Bun supporte nativement TypeScript strict sans configuration particulière. Le template officiel (`bun init`) active déjà `"strict": true` :

```json
{
  "compilerOptions": {
    "lib": ["ESNext"],
    "target": "ESNext",
    "module": "Preserve",
    "moduleResolution": "bundler",
    "strict": true,
    "skipLibCheck": true,
    "noEmit": true,
    "noUncheckedIndexedAccess": true,
    "experimentalDecorators": true,
    "emitDecoratorMetadata": true
  }
}
```

**Point important** : Bun transpile TypeScript mais **ne vérifie pas les types** — utiliser `bunx tsc --noEmit` pour la validation. Les décorateurs legacy (`experimentalDecorators`) et `emitDecoratorMetadata` fonctionnent, essentiels si vous utilisez des patterns DI.

Un problème connu : les options définies dans un tsconfig parent via `extends` peuvent ne pas être lues correctement. **Solution** : déclarer toutes les options directement dans le tsconfig du projet.

---

## Conclusion et recommandation

Pour votre serveur MCP RAG sémantique sur Intel i5-4570/24GB, **Node.js reste le choix prudent** compte tenu du transport stdio. Le bug de blocage stdin ≥16KB et les memory leaks documentés représentent des risques concrets pour un serveur devant tourner en continu.

**Configuration recommandée si vous optez pour Bun malgré les risques** :
- Utiliser postgres.js + drizzle-orm/postgres-js (éviter bun-sql)
- Implémenter un monitoring mémoire avec alertes
- Configurer systemd avec `Restart=always` et `RestartSec` court
- Limiter la taille des messages MCP pour rester sous 16KB
- Tester extensivement le comportement stdio avec votre workflow Claude Code

**Alternative pragmatique** : développer et prototyper avec Bun pour sa DX supérieure, puis déployer en production avec Node.js 22+ via un simple changement de runtime — le code TypeScript reste compatible.