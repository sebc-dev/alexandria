---
description: 'Analyse complete incluant mutation testing (PIT) - ~10-15 minutes. A executer avant PR/merge.'
allowed-tools: Bash(make:*), Bash(./scripts/full-analysis.sh:*), Read
---

# Full Analysis - Analyse Complete

Execute l'analyse de qualite COMPLETE incluant mutation testing (PIT).

## Avertissement

Cette commande prend environ 10-15 minutes. Confirme avec l'utilisateur avant de lancer si le contexte n'est pas clair.

## Execution

Lance la commande:

```bash
cd "$CLAUDE_PROJECT_DIR" && make full-check
```

Ou directement:

```bash
cd "$CLAUDE_PROJECT_DIR" && ./scripts/full-analysis.sh
```

## Etapes executees

1. Compilation avec Error Prone
2. Tests unitaires
3. PMD + CPD (detection copier-coller)
4. SpotBugs (bugs potentiels)
5. Checkstyle (conventions de code)
6. PIT Mutation Testing (qualite des tests)

## Interpretation des Resultats

### Rapport PIT

Si mutation testing echoue:
- Lire `target/pit-reports/index.html`
- Identifier les mutants survivants
- Suggerer des tests supplementaires pour les tuer

### Seuils

- Mutation coverage minimum: 80% (configurable dans pom.xml)
- Line coverage minimum: 70%
