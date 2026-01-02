# CLI Specification (F21)

**Parser:** `Bun.argv` + `node:util` parseArgs (zero deps)

**Commands:**

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
alexandria delete README.md
```

**Exit codes:**

| Code | Signification |
|------|---------------|
| 0 | Succès |
| 1 | Erreur de validation (fichier manquant, JSON invalide) |
| 2 | Erreur d'exécution (DB, embedding) |
| 3 | Document non trouvé (pour delete) |
