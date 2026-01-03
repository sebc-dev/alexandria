# CLI Specification (F21)

**Status:** ✅ VALIDÉ (janvier 2026)

## Parser Configuration

**Parser:** `node:util` parseArgs (zero deps, natif Bun)

**Configuration requise:**

```typescript
import { parseArgs } from "node:util";

const { values, positionals } = parseArgs({
  args: process.argv.slice(2),  // ⚠️ Utiliser process.argv, PAS Bun.argv
  options: {
    "chunks-file": { type: "string", short: "c" },
    title: { type: "string", short: "T" },
    tags: { type: "string", short: "t" },
    version: { type: "string", short: "v" },
    upsert: { type: "boolean", default: true },
    "dry-run": { type: "boolean", short: "d" },
    help: { type: "boolean", short: "h" },
  },
  strict: true,
  allowPositionals: true,
  allowNegative: true,  // ⚠️ Support --no-upsert (Bun 1.2.11+)
});
```

**Contraintes parseArgs:**

| Paramètre | Valeur | Raison |
|-----------|--------|--------|
| `args` | `process.argv.slice(2)` | ⚠️ Évite bugs Bun #15877, #22157 avec `Bun.argv` |
| `strict` | `true` | Échec sur flags inconnus |
| `allowPositionals` | `true` | Support `<file>` et `<source>` |
| `allowNegative` | `true` | Support `--no-upsert` → `{ upsert: false }` |

**Compatibilité Bun:**

| Version | Support |
|---------|---------|
| 1.1.24+ | ✅ parseArgs basique fonctionne |
| 1.2.11+ | ✅ `allowNegative` supporté |
| 1.3.5+ | ✅ Recommandé (fix NAPI hot reload) |

## Commands

```bash
# Ingestion
alexandria ingest <file> [options]
  -c, --chunks-file <path>   Fichier JSON des chunks (requis)
  -T, --title <title>        Titre du document (F36, optionnel)
  -t, --tags <tags>          Tags séparés par virgules
  -v, --version <version>    Version du document
  -u, --upsert               Remplacer si existe (défaut: true)
  -n, --no-upsert            Erreur si existe
  -d, --dry-run              Valider sans persister
  -h, --help                 Afficher l'aide

# Suppression
alexandria delete <source>
  -h, --help                 Afficher l'aide

# Exemples
alexandria ingest README.md -c chunks.json -t "docs,readme" -v "1.0.0"
alexandria ingest doc.md -c c.json -T "Guide API" --dry-run
alexandria ingest guide.md --chunks-file c.json --title "User Guide" -t "docs"
alexandria ingest guide.md --no-upsert -c chunks.json  # Erreur si existe
alexandria delete README.md
```

## Exit Codes (Convention POSIX)

| Code | Nom | Signification | Exemples |
|------|-----|---------------|----------|
| 0 | SUCCESS | Commande exécutée sans erreur | Ingestion réussie, suppression réussie |
| 1 | RUNTIME_ERROR | Erreur d'exécution | Échec DB, échec embedding, réseau |
| 2 | VALIDATION_ERROR | Erreur de syntaxe/validation | Flag inconnu, JSON invalide, argument manquant |

**Conventions:**

- **Exit 1** : Erreurs runtime (connexion DB échouée, embedding timeout, fichier introuvable à l'exécution)
- **Exit 2** : Erreurs de validation utilisateur (flag `--xyz` inconnu, chunks.json mal formé, argument `<file>` manquant)

**Note:** Document non trouvé (delete) → Exit 1 (erreur runtime, pas une erreur de syntaxe)

**Codes sysexits.h optionnels (pour granularité future):**

| Code | Nom | Usage potentiel |
|------|-----|-----------------|
| 64 | EX_USAGE | Erreur d'usage CLI spécifique |
| 65 | EX_DATAERR | Format de données invalide |
| 66 | EX_NOINPUT | Fichier d'entrée introuvable |

## Validation Manuelle Requise

parseArgs ne valide pas automatiquement les positionnels. Implémenter:

```typescript
if (positionals.length < 1) {
  console.error("Usage: alexandria ingest <file> -c <chunks-file>");
  process.exit(2);  // Exit 2 = validation error
}

if (!values["chunks-file"]) {
  console.error("Error: --chunks-file is required");
  process.exit(2);
}

const [file] = positionals;
```

## Limitations parseArgs

| Limitation | Impact | Mitigation |
|------------|--------|------------|
| Types: string/boolean uniquement | Pas de number natif | Parser manuellement si besoin |
| Pas de validation intégrée | Options requises manuelles | Code de validation explicite |
| Pas de génération d'aide | `--help` manuel | Implémenter printUsage() |
| Pas de subcommands | `git clone`-style impossible | Utiliser tokens ou lib externe |

## Distribution

```bash
# Build production
bun build --compile --minify ./src/cli/ingest.ts --outfile dist/alexandria

# Cross-compilation (optionnel)
bun build --compile --target=bun-linux-x64 ./src/cli/ingest.ts --outfile dist/alexandria-linux
bun build --compile --target=bun-darwin-arm64 ./src/cli/ingest.ts --outfile dist/alexandria-macos
```
