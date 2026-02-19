# Tests unitaires — Principes fondamentaux

## Philosophie

- Tester le **comportement observable**, jamais l'implémentation interne
- Test de validation : "Si je refactore l'implémentation sans changer la fonctionnalité, ce test passe-t-il encore ?" Si non → réécrire le test
- Hiérarchie de vérification : output-based (retours) > state-based (état) > communication-based (mocks)
- Un test = un concept logique unique

## Structure AAA obligatoire

Chaque test suit Arrange-Act-Assert avec séparation par ligne vide :

```
// Arrange — préparer le SUT et les données
// (ligne vide)
// Act — UNE SEULE ligne d'exécution
// (ligne vide)
// Assert — vérifier le comportement observable
```

La section Act doit tenir en **une seule ligne**. Plusieurs lignes = le test vérifie plus d'un comportement → scinder.

## Nommage

Format : `description_du_scenario_et_resultat_attendu` en langage naturel.
- ✅ `delivery_with_past_date_is_invalid`
- ✅ `cart_applies_discount_when_total_exceeds_threshold`
- ❌ `testIsDeliveryValid`, `test1`, `itWorks`

Le nom doit permettre de comprendre le scénario **sans lire le code du test**.

## Sélection des cas de test (EP + BVA)

Pour chaque fonctionnalité, tester systématiquement :
1. **Happy path** — chemin nominal
2. **Boundary values** — valeurs limites (ex: 17, 18, 19 pour seuil 18)
3. **Erreurs** — entrées invalides, null/undefined/vide
4. **Edge cases** — collections vides, chaînes vides, zéro, négatifs

Isoler les partitions invalides (une par test) pour identifier précisément la cause d'échec.

## Doubles de test — usage minimal

```
Dummy  → Remplit un paramètre, jamais utilisé
Stub   → Réponses pré-programmées (simule des entrées)
Fake   → Implémentation simplifiée (in-memory DB)
Mock   → Vérification de comportement sortant (DERNIER RECOURS)
```

Règles strictes :
- Mocker **uniquement** les dépendances hors processus non gérées (API externes, SMTP, bus de messages)
- Utiliser des collaborateurs réels pour les dépendances internes
- Préférer les fakes aux mocks quand possible
- **> 2-3 doubles dans un test = signal de refactoring du SUT**
- "Only mock types that you own" → encapsuler les libs tierces dans des adaptateurs

## Propriétés FIRST

- **Fast** : < 100ms par test ; zéro appel réseau/filesystem réel
- **Isolated** : exécutable seul, dans n'importe quel ordre ; zéro état mutable partagé
- **Repeatable** : déterministe ; horloge injectable, random avec seed fixe
- **Self-validating** : assertions explicites, jamais de vérification manuelle
- **Timely** : écrits au moment du développement

## Couverture

- Indicateur **négatif** utile : faible couverture = certainement sous-testé
- **Jamais** un objectif : haute couverture ≠ bien testé
- Repères Google : 60% acceptable, 75% bon, 90% exemplaire
- Ne jamais écrire de tests sans assertions pour gonfler la couverture

## Classification du code (Khorikov)

| | Peu de collaborateurs | Beaucoup de collaborateurs |
|---|---|---|
| **Haute complexité** | DOMAINE → tester unitairement (priorité) | SURCOMPLIQUÉ → refactorer d'abord |
| **Basse complexité** | TRIVIAL → ne pas tester | CONTRÔLEURS → tests d'intégration |

Ne pas tester : getters/setters triviaux, code sans logique conditionnelle, wrappers sans transformation.