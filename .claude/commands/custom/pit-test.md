---
description: 'Execute uniquement les tests de mutation PIT - ~8-15 minutes'
allowed-tools: Bash(make:*), Bash(mvn:*), Read
---

# PIT Mutation Testing

Execute uniquement les tests de mutation pour evaluer la qualite des tests.

## Execution

Lance la commande:

```bash
cd "$CLAUDE_PROJECT_DIR" && make pit
```

## Qu'est-ce que le mutation testing?

PIT introduit des "mutants" (petites modifications) dans le code:
- Change `>` en `>=`
- Change `true` en `false`
- Supprime des lignes de code

Un bon test doit "tuer" ces mutants en echouant quand le code est modifie.

## Interpretation du Rapport

Ouvre le rapport:

```bash
make open-pit
```

Ou:

```bash
xdg-open target/pit-reports/index.html
```

### Mutants Survivants

Indiquent des tests insuffisants. Pour chaque mutant survivant:
1. Identifie la ligne de code
2. Comprends quelle modification a ete faite
3. Suggere un test qui aurait detecte cette modification
