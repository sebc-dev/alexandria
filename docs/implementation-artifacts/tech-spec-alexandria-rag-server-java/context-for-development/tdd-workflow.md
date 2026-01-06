# TDD Workflow

## Cycle Red-Green-Refactor

Chaque task suit obligatoirement ce cycle :

### 1. RED (Test First)
```
1. Créer le fichier test AVANT le fichier d'implémentation
2. Écrire les cas de test qui définissent le comportement attendu
3. Exécuter les tests → ils DOIVENT échouer (compilation ou assertion)
4. Commit optionnel : "test(scope): RED - add failing tests for [feature]"
```

### 2. GREEN (Make It Work)
```
1. Écrire le MINIMUM de code pour faire passer les tests
2. Pas d'optimisation, pas de "nice to have"
3. Exécuter les tests → ils DOIVENT passer
4. Commit : "feat(scope): GREEN - implement [feature]"
```

### 3. REFACTOR (Make It Clean)
```
1. Améliorer le code SANS changer le comportement
2. Les tests doivent continuer à passer
3. Commit : "refactor(scope): clean up [feature]"
```

## Règles Strictes

| Règle | Description |
|-------|-------------|
| **No Code Without Test** | Aucun code de production sans test qui le couvre |
| **Minimal Implementation** | Implémenter uniquement ce que les tests demandent |
| **Tests Are Documentation** | Les tests définissent le contrat de l'API |
| **Fast Feedback** | Exécuter les tests après chaque modification |

## Exceptions (Code Non-Testable)

Certains éléments ne nécessitent pas de TDD :

- **Phase 1** : Scaffold projet (pom.xml, main class, configs YAML)
- **Phase 11** : Schema SQL (testé indirectement via intégration)
- **Phase 14** : Docker (testé manuellement ou via CI)

## Structure des Tests

```
src/test/java/dev/alexandria/
├── test/                    # Test infrastructure (fixtures, stubs, support)
│   ├── EmbeddingFixtures.java
│   ├── InfinityStubs.java
│   ├── McpTestSupport.java
│   └── PgVectorTestConfiguration.java
├── core/                    # Unit tests
│   ├── QueryValidatorTest.java
│   ├── AlexandriaMarkdownSplitterTest.java
│   └── ...
├── adapters/                # Integration tests
│   ├── InfinityClientRetryTest.java
│   └── ...
└── McpToolsE2ETest.java     # E2E tests
```

## Commandes Utiles

```bash
# Exécuter tous les tests
./mvnw test

# Exécuter un test spécifique
./mvnw test -Dtest=QueryValidatorTest

# Exécuter avec couverture
./mvnw test jacoco:report

# Watch mode (avec Maven Daemon)
mvnd test -Dtest=QueryValidatorTest --watch
```

## Pyramide de Tests

```
        /\
       /E2E\        10% - McpSyncClient flows
      /------\
     /Integ   \     20% - Testcontainers, WireMock
    /----------\
   /   Unit     \   70% - JUnit 5, Mockito
  /--------------\
```
