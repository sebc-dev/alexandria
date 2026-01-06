# Alexandria Project Context

> Ce fichier définit les règles et patterns critiques que tous les agents IA doivent suivre lors de l'implémentation.

## Projet

- **Nom**: Alexandria RAG Server
- **Stack**: Java (voir tech-spec pour détails)
- **Type**: Serveur RAG (Retrieval-Augmented Generation)

## Pratiques de Développement

### TDD Obligatoire

**Red-Green-Refactor** est la seule approche acceptée:

1. **RED** — Écrire un test qui échoue AVANT d'écrire le code de production
2. **GREEN** — Écrire le code minimal pour faire passer le test
3. **REFACTOR** — Nettoyer le code tout en gardant les tests verts

#### Règles TDD strictes

- [ ] Aucun code de production sans test correspondant écrit EN PREMIER
- [ ] Les tests doivent être exécutables et échouer avant l'implémentation
- [ ] Commits séparés: test (red) → impl (green) → refactor si nécessaire
- [ ] Coverage minimum: à définir selon les besoins du projet

#### Structure des tests

```
src/
├── main/java/         # Code de production
└── test/java/         # Tests unitaires et d'intégration
```

## Références

- Tech Spec: `docs/implementation-artifacts/tech-spec-alexandria-rag-server-java/`
- Research: `docs/research/`
