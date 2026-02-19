---
paths:
  - "src/test/**/*Test.java"
  - "src/test/**/*IT.java"
  - "src/test/**/*Tests.java"
---
# Écriture de tests — Anti-patterns et qualité

## Anti-patterns INTERDITS

| Anti-pattern | Détection | Correction |
|---|---|---|
| **The Liar** — test sans assertion réelle | `expect` absent ou trivial | Vérifier un comportement observable concret |
| **The Mockery** — plus de mocks que d'assertions | Compter mocks vs assertions | Réduire les doubles, utiliser des collaborateurs réels |
| **The Inspector** — accès aux membres privés | Réflexion, cast, `as any` | Tester via l'API publique uniquement |
| **The Giant** — test > 50 lignes | Longueur excessive | Scinder en tests focalisés (1 concept = 1 test) |
| **Fragile Test** — casse au refactoring sans bug | Vérifie le "comment" pas le "quoi" | Asserter sur sorties et effets de bord observables |
| **The Nitpicker** — compare une sortie entière | `toEqual` sur objet/JSON complet | Asserter uniquement sur les champs pertinents |
| **Free Ride** — assertion non liée ajoutée à un test existant | Assertion sans rapport avec le nom du test | Créer un nouveau test pour chaque comportement |
| **Flaky** — résultat non déterministe | `sleep`, `Date.now()`, réseau | Horloge injectable, seed fixe, zéro I/O réel |

## Checklist avant de valider un test

```
□ Le nom décrit le scénario ET le résultat attendu ?
□ Structure AAA avec Act en une seule ligne ?
□ Les assertions vérifient le comportement observable (pas l'implémentation) ?
□ Le test survivrait à un refactoring interne du SUT ?
□ Boundary values et cas d'erreur couverts ?
□ Le test est déterministe et indépendant ?
□ Nombre de doubles ≤ 2-3 ?
□ Pas de sleep/wait temporels ?
□ Pas d'accès aux membres privés ?
```

## Checklist anti-flakiness

```
□ Horloge injectable (pas de Date.now() / new Date() direct)
□ Zéro appel réseau réel
□ Zéro accès filesystem réel (ou abstrait)
□ Zéro état mutable partagé entre tests
□ Random avec seed fixe
□ Assertions indépendantes de l'ordre des collections
□ Pas de sleep/setTimeout dans les tests
```

## Données de test

- Utiliser le pattern **Test Data Builder** avec valeurs par défaut sensibles
- Spécifier **uniquement** les champs pertinents au comportement testé
- Fresh fixtures par défaut ; jamais de shared mutable state entre tests
- Labels lisibles pour chaque jeu de paramètres dans les tests paramétrés
- Ne jamais mélanger chemins de succès et chemins d'erreur dans un même test paramétré

## DAMP > DRY dans les tests

- **Scénarios** (what-to) : inline, descriptifs, explicites dans chaque test → DAMP
- **Mécanismes** (how-to) : builders, factories, assertions custom → DRY
- Ne jamais extraire de `beforeEach` qui obscurcit l'intention du test
- Chaque test doit être compréhensible en isolation

## Quand supprimer un test

1. Fonctionnalité supprimée ou obsolète
2. Couplé à l'implémentation (casse à chaque refactoring)
3. Dupliqué par un test de scope plus large
4. Non rattachable à une exigence métier
5. Définitivement `@Ignored`/skippé (test mort)
6. Flaky irréparable