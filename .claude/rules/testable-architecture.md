---
paths:
  - "src/main/**/*.java"
---
# Architecture testable — Principes de conception

## Functional Core, Imperative Shell

Séparer systématiquement :
- **Noyau fonctionnel** : fonctions pures (entrées → sorties, zéro effet de bord). Contient toute la logique métier, décisions, transformations. Testable unitairement sans mock.
- **Shell impérative** : couche mince gérant les I/O (DB, réseau, filesystem). Peu/pas de conditions. Testable en intégration.

Principe : **séparer la décision de l'action**. Le code calcule *quoi faire* (pur), un orchestrateur *exécute* (impur).

## Injection de dépendances

- Chaque dépendance externe doit être **injectable**
- Les abstractions sont définies par le **consommateur**, pas le fournisseur (DIP)
- Pas de singletons hard-codés, pas d'appels statiques avec effets de bord
- Chaque dépendance hard-codée = un seam manqué = testabilité dégradée

## Humble Object pattern

Quand du code est à la fois complexe ET fortement connecté :
1. Extraire la logique complexe dans un objet **pur et isolé** → tester unitairement
2. Garder l'orchestrateur **simple mais connecté** → tester en intégration

## Effets de bord

Repousser aux frontières du système :
- I/O, mutation d'état global, appels réseau → toujours dans la shell
- Horloge, aléatoire → injecter des abstractions contrôlables
- Jamais d'effet de bord dans la logique métier