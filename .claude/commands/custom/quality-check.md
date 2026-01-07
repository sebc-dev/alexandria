---
description: 'Analyse qualite rapide (PMD, SpotBugs, Checkstyle) sans mutation testing - ~2-5 minutes'
allowed-tools: Bash(make:*), Bash(mvn:*), Read
---

# Quality Check - Analyse Rapide

Execute l'analyse de qualite complete SANS mutation testing (PIT).

## Execution

Lance la commande:

```bash
cd "$CLAUDE_PROJECT_DIR" && make quick-check
```

## Interpretation des Resultats

### En cas de succes

Indique a l'utilisateur que le code est pret pour push/PR.

### En cas d'echec

1. Identifie quel outil a echoue (PMD, SpotBugs, Checkstyle)
2. Propose de lire le rapport correspondant:
   - PMD: `target/pmd.html`
   - SpotBugs: `target/spotbugsXml.xml`
   - Checkstyle: `target/checkstyle-result.xml`
3. Propose des corrections si possible

## Post-verification

Si tout passe, rappelle que `/full-analysis` ou `make full-check` inclut aussi les tests de mutation (PIT) pour une validation complete avant merge.
