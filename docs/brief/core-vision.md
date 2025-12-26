# Core Vision

## Problem Statement

En tant que développeur unique utilisant Claude Code pour gérer plusieurs projets, le risque principal est la **dérive technique silencieuse**. Les agents IA accèdent à une connaissance universelle générique ou obsolète, et sans garde-fous stricts, introduisent:

- **Dette technique invisible**: Détectée tardivement (code review, bugs, revisites)
- **Violations de conventions**: Code non conforme aux standards projet
- **Technologies obsolètes**: Utilisation de patterns ou APIs dépassés
- **Friction cognitive constante**: Nécessité de rappeler manuellement les conventions à chaque tâche

Le processus actuel repose sur des fichiers markdown manuels qu'il faut penser à inclure au bon moment - et quand on oublie, on paie le prix en refactoring, bugs, failles de sécurité et problèmes de performance.

## Problem Impact

**Impact immédiat:**
- **Temps perdu**: 10+ minutes par feature à rappeler les conventions
- **Itérations multiples**: 3-5 cycles de correction par tâche
- **Frustration maximale**: "Rappelle-toi comment on fait ICI" répété sans cesse
- **Review lourde**: Vérification ligne par ligne nécessaire

**Impact à long terme:**
- Bugs introduits par non-respect des conventions
- Refactoring coûteux de code non-conforme
- Difficultés de maintenance croissantes
- Failles de sécurité et problèmes de performance

**Coût cognitif:**
- Impossible de gérer efficacement plus de 2-3 projets simultanés
- Chaque switch de projet nécessite de recharger mentalement les bonnes conventions
- Perte de confiance dans le code généré

## Why Existing Solutions Fall Short

**Solutions explorées:**

1. **Fichiers markdown manuels** (approche actuelle)
   - ❌ Maintenance lourde et oublis fréquents
   - ❌ Ajoutent du bruit au contexte LLM
   - ❌ Documentation séparée des conventions (pas pratique)

2. **CodeRabbit** (review automatisée)
   - ❌ Détection tardive (après génération complète)
   - ❌ Coûts élevés en re-génération (2-3x tokens)
   - ❌ Ne prévient pas les choix architecturaux incompatibles

3. **Serveurs MCP** (accès documentation)
   - ❌ Fournissent la doc mais sans filtrage de conformité
   - ❌ Pas de stratification Convention > Documentation

4. **Cognee** (RAG pour code)
   - ❌ Architecture trop lourde (3 bases de données: relationnelle + vectorielle + graph)
   - ❌ Projet encore trop jeune et immature

5. **Letta/Letta Code** (mémoire agent)
   - ❌ Dépendance à API externe
   - ❌ Limites/coûts incertains, perte de contrôle

6. **LightRAG, Mem0** (RAG indirect)
   - ❌ Pas centré sur le code
   - ❌ RAG classique sans hiérarchie de contexte

**Le problème fondamental:** Toutes les solutions sont soit **réactives** (détection tardive), soit **lourdes** (over-engineered), soit **externes** (perte de contrôle). Aucune ne fait de **filtrage actif de conformité** au moment où l'agent génère le code.

## Proposed Solution

Alexandria résout le problème fondamental via un **Active Compliance Filter** qui fusionne proactivement conventions projet et documentation technique.

**Architecture en 3 layers:**

1. **Layer 1 - Conventions** (Priorité absolue)
   - Règles non-négociables du projet
   - Patterns imposés, interdictions, linting
   - Présentées comme des "lois" à l'agent

2. **Layer 2 - Documentation technique** (Vocabulaire contraint)
   - APIs, frameworks, bibliothèques
   - Documentation toujours à jour (crawling automatique)
   - Contextualisée par les conventions Layer 1

3. **Layer 3 - Reformulation** (Synthèse mono-approche)
   - LLM de reformulation fusionne Conv + Doc
   - Génère un "guide d'implémentation contraint"
   - Élimine les patterns contradictoires

**Workflow d'utilisation:**

```
Développeur → Demande feature
     ↓
Alexandria Skill (auto/manuel)
     ↓
Active Compliance Filter
     ↓
Claude Code reçoit contexte fusionné
     ↓
Code parfait dès 1ère génération ✓
```

**Intégration native:**
- Skill Alexandria appelé automatiquement aux moments opportuns
- Custom slash commands intégrant le skill
- Custom sub-agents avec Alexandria pré-configuré
- Workflow Brief→PRD→Epic→Story→Commit avec contexte maintenu

**Caractéristiques clés:**
- ✅ **Auto-hébergé**: Contrôle total, coûts maîtrisés
- ✅ **Personnalisable**: Architecture adaptable aux besoins
- ✅ **Proactif**: Prévient vs détecte
- ✅ **Contextuel**: S'adapte au projet actif
- ✅ **Toujours à jour**: Crawling automatique des docs

## Key Differentiators

**vs RAG Classique:**
- RAG traditionnel retourne info brute sans hiérarchie (5 docs "error handling" de frameworks différents)
- Alexandria stratifie: Conventions (Layer 1) > Doc (Layer 2) > Reformulation (Layer 3)
- L'agent ne peut plus "choisir" entre patterns contradictoires

**vs Review Post-Génération:**
- Validation réactive détecte après génération complète → cycles coûteux (2-3x tokens)
- Choix architecturaux incompatibles nécessitent refactor profond
- Alexandria prévient en amont → génération conforme dès le départ
- **Évite 70% des itérations** et garantit cohérence structurelle

**vs Solutions Externes (Letta, Cognee):**
- Auto-hébergé → aucune limite, aucun coût récurrent
- Contrôle total de l'architecture et des données
- Personnalisation illimitée selon besoins évolutifs
- Pas de dépendance à des services tiers

**Valeur unique:**
Le moment "wow" où développer avec Claude Code passe de:
- **Avant**: 10 min de "rappelle-toi comment on fait ICI"
- **Après**: 30 sec de génération parfaite

Transformer Claude Code de "stagiaire à surveiller" en "senior dev qui connaît ton projet par cœur".

---
