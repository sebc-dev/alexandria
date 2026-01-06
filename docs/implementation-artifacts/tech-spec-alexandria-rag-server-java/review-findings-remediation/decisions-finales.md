# Decisions Finales - Revue Tech-Spec Alexandria RAG Server

**Date:** 2026-01-06
**Statut:** Valide

## Resume Executif

Sur 18 recommandations analysees:
- **5 ACCEPTEES** - A integrer dans l'implementation-plan
- **7 REPORTEES** - Post-MVP
- **6 REJETEES** - Non pertinentes ou deja couvertes

---

## Tableau Decisionnel

| ID | Severite | Titre | Verdict | Priorite | Action |
|----|----------|-------|---------|----------|--------|
| F1 | CRITICAL | @Transactional sur DELETE+INSERT | ACCEPTER | MUST HAVE | Modifier Task 21 + Task 3 |
| F2 | CRITICAL | Rate Limiting Infinity API | PARTIEL | SHOULD HAVE | Enrichir Task 13 (Bulkhead optionnel) |
| F3 | CRITICAL | Auth sur /mcp | REJETER | NICE TO HAVE v2 | Localhost suffit |
| F4 | HIGH | getStoredDocumentInfo() manquante | ACCEPTER | MUST HAVE | Enrichir Task 21 + Task 7 |
| F5 | HIGH | logback-spring.xml manquant | ACCEPTER | SHOULD HAVE | Nouvelle Task 3b |
| F6 | HIGH | Tests de concurrence | REPORTER | POST-MVP | Single-user, F1 couvre le risque |
| F7 | HIGH | Testcontainers + Java 25 | REJETER | N/A | Risque theorique, documenter |
| F8 | MEDIUM | CLI Error ACs | REPORTER | POST-MVP | Usage dev local controle |
| F9 | MEDIUM | llms.txt chunking undefined | DOCUMENTER | LOW | Ajouter dans chunking-strategy |
| F10 | MEDIUM | Token estimation naive | DEJA FAIT | N/A | Task 14+17 prevoient Tokenizer |
| F11 | MEDIUM | Graceful shutdown | MINIMAL | LOW | 2 lignes YAML dans Task 3 |
| F12 | MEDIUM | schema.sql SET non persistant | ACCEPTER | SHOULD HAVE | connection-init-sql dans Task 3 |
| F13 | MEDIUM | Cold start detection | REJETER | N/A | Task 11 prevoit globalColdStart |
| F14 | MEDIUM | Tests HTML Jsoup | REPORTER | POST-MVP | Jsoup mature, format secondaire |
| F15 | MEDIUM | RAG Metrics Micrometer | REPORTER | POST-MVP | JFR + logs suffisent |
| F16 | LOW | Stopwords francais | ACCEPTER | LOW | Integrer dans Task 8 |
| F17 | LOW | .env.example | SKIP | POST-MVP | docker-compose suffit |
| F18 | LOW | Tests progress MCP | REPORTER | POST-MVP | Test unitaire mock suffit |

---

## Details par Recommandation

### F1: @Transactional sur DELETE+INSERT

**Severite originale:** CRITICAL
**Verdict:** ACCEPTER - MUST HAVE

**Analyse:**
- Le `DocumentUpdateService.ingestDocument()` execute DELETE puis INSERT sans `@Transactional`
- Risque de perte de donnees si crash entre les deux operations
- L'intention "transactionnel" est documentee dans la research mais pas l'annotation

**Actions:**
1. Modifier Task 21: Ajouter `@Transactional(isolation = READ_COMMITTED)` sur `ingestDocument()`
2. Modifier Task 3: Ajouter `spring.datasource.hikari.auto-commit: false`

**Impact:** Correction simple (1 annotation + 1 ligne config), risque eleve si non corrige

---

### F2: Rate Limiting pour Infinity API

**Severite originale:** CRITICAL
**Verdict:** PARTIEL - SHOULD HAVE

**Analyse:**
- Task 13 prevoit deja Retry avec exponential backoff
- RateLimiter et Bulkhead proposes mais non prevus
- Pour RunPod self-hosted, pas de risque de bannissement

**Actions:**
1. Enrichir Task 13: Ajouter RateLimiter (10 req/s) + Bulkhead (5 concurrent)
2. Modifier Task 19: Ajouter annotations `@RateLimiter` et `@Bulkhead`

**Impact:** Utile pour protection cold start cascade, pas critique pour MVP

---

### F3: Auth sur /mcp Endpoint

**Severite originale:** CRITICAL
**Verdict:** REJETER - Reclasser NICE TO HAVE v2

**Analyse:**
- Usage prevu: localhost mono-utilisateur avec Claude Desktop/Code
- La propre research du projet confirme: "localhost = aucune auth necessaire"
- `server.address: 127.0.0.1` suffit pour isoler le service

**Actions:**
- Aucune pour v1
- Reporter a v2 si exposition LAN envisagee

**Impact:** Complexite evitee (Spring Security, tests, config Claude Desktop)

---

### F4: getStoredDocumentInfo() Non Implementee

**Severite originale:** HIGH
**Verdict:** ACCEPTER - MUST HAVE

**Analyse:**
- Task 21 mentionne "two-phase change detection" mais la methode n'est pas implementee
- Sans cette methode, le fast path mtime+size ne fonctionne pas
- Chaque re-ingestion recalculera inutilement le hash SHA-256

**Actions:**
1. Enrichir Task 21: Implementer `getStoredDocumentInfo()` avec requete JSONB
2. Enrichir Task 7: Ajouter `fileSize` et `fileModifiedAt` dans ChunkMetadata

**Impact:** Bloque le fast path, performance d'ingestion degradee

---

### F5: logback-spring.xml Manquant

**Severite originale:** HIGH
**Verdict:** ACCEPTER - SHOULD HAVE

**Analyse:**
- Aucune Task pour creer logback-spring.xml
- AC 12 exige "correlationId present dans les logs"
- Sans config custom, le correlationId du MDC ne sera pas visible

**Actions:**
1. Creer Task 3b: logback-spring.xml simplifie (pas ECS, juste pattern avec correlationId)

**Configuration minimale:**
```xml
<pattern>%d{HH:mm:ss.SSS} %5p [%X{correlationId:-NO_CID}] %-40.40logger{39} : %m%n</pattern>
```

**Impact:** Satisfait AC 12, debugging facilite

---

### F6: Tests de Concurrence Absents

**Severite originale:** HIGH
**Verdict:** REPORTER - POST-MVP

**Analyse:**
- Usage single-user local rend les race conditions improbables
- F1 (@Transactional) couvre le risque principal de corruption
- Tests de concurrence = complexite pour risque faible

**Actions:**
- Reporter a v2 si multi-utilisateur
- Documenter le risque accepte

**Impact:** Risque accepte pour MVP mono-utilisateur

---

### F7: Testcontainers 2.x + Java 25

**Severite originale:** HIGH
**Verdict:** REJETER - Reclasser LOW (documentation)

**Analyse:**
- Testcontainers 2.0.3 est recent (dec 2025), aucune issue Java 25 connue
- La solution H2 proposee est invalide: H2 ne supporte pas pgvector
- Le projet n'utilise pas de features exclusives Java 25

**Actions:**
- Documenter le risque accepte dans notes.md
- Surveiller les releases Testcontainers

**Impact:** Risque theorique, pas d'action technique

---

### F8: CLI Error ACs Manquants

**Severite originale:** MEDIUM
**Verdict:** REPORTER - POST-MVP

**Analyse:**
- Usage developpeur local = environnement controle
- Erreurs I/O standard de Java suffisent pour v1
- Edge cases (encodage, symlinks, disque plein) rares

**Actions:**
- Reporter si feedback utilisateurs
- Ajouter --dry-run AC (deja dans Task 29)

**Impact:** UX amelioree mais non bloquant

---

### F9: llms.txt Chunking Non Defini

**Severite originale:** MEDIUM
**Verdict:** DOCUMENTER - LOW

**Analyse:**
- Task 18 definit le parser mais pas la strategie de chunking
- "Un chunk par section H2" est coherent avec la strategie markdown

**Actions:**
- Documenter dans chunking-strategy-from-research-15.md

**Impact:** Clarification, pas de changement technique

---

### F10: Token Estimation Naive

**Severite originale:** MEDIUM
**Verdict:** DEJA FAIT - Aucune action

**Analyse:**
- Task 14 prevoit explicitement: `Tokenizer (OpenAiTokenizer "gpt-4o-mini")`
- Task 17 utilise ce Tokenizer
- La recommandation F10 est obsolete

**Actions:** Aucune - verifier a l'implementation

**Impact:** Deja couvert dans la spec

---

### F11: Graceful Shutdown Absent

**Severite originale:** MEDIUM
**Verdict:** MINIMAL - LOW

**Analyse:**
- Spring Boot supporte graceful shutdown mais pas active par defaut
- F1 (@Transactional) protege l'integrite des donnees
- 2 lignes YAML suffisent

**Actions:**
- Ajouter dans Task 3:
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

**Impact:** Protection minimale, effort minimal

---

### F12: schema.sql SET Non Persistant

**Severite originale:** MEDIUM
**Verdict:** ACCEPTER - SHOULD HAVE

**Analyse:**
- `SET hnsw.ef_search=100` dans schema.sql ne persiste pas
- Sans cette config, recall vectoriel degrade de 15-20%
- Filtrage JSONB inefficace sans `iterative_scan`

**Actions:**
- Ajouter dans Task 3:
```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: |
        SET hnsw.ef_search = 100;
        SET hnsw.iterative_scan = relaxed_order;
```

**Impact:** Performance de recherche significativement amelioree

---

### F13: Cold Start Detection Manquante

**Severite originale:** MEDIUM
**Verdict:** REJETER - Deja couvert

**Analyse:**
- Task 11 prevoit explicitement `globalColdStart: 90s` et `globalWarm: 30s`
- AC 8 couvre le cold start
- L'InfinityColdStartTracker propose est over-engineering

**Actions:** Aucune - deja couvert

**Impact:** Spec actuelle suffisante

---

### F14: Tests HTML Jsoup Absents

**Severite originale:** MEDIUM
**Verdict:** REPORTER - POST-MVP

**Analyse:**
- Jsoup est une librairie mature (15+ ans, millions d'utilisateurs)
- HTML est un format secondaire (Markdown = principal)
- Les tests proposes testent Jsoup, pas notre code

**Actions:** Reporter a v2 si besoin validation

**Impact:** Faible risque, faible valeur ajoutee

---

### F15: RAG Latency Metrics Absentes

**Severite originale:** MEDIUM
**Verdict:** REPORTER - POST-MVP

**Analyse:**
- JFR (Java Flight Recorder) deja documente comme outil de profiling
- Logs de timing simples suffisent pour debugging
- Grafana sans sens pour mono-utilisateur

**Actions:**
- Ajouter logs de timing dans RetrievalService (optionnel)
- Reporter Micrometer Timers a v2

**Impact:** Over-engineering pour usage local

---

### F16: Stopwords Anglais Uniquement

**Severite originale:** LOW
**Verdict:** ACCEPTER - LOW

**Analyse:**
- Configuration projet en francais (config.yaml)
- BGE-M3 est multilingue, requetes FR attendues
- Ajout trivial (15-30 min)

**Actions:**
- Integrer stopwords FR dans Task 8:
```java
private static final Set<String> STOPWORDS_FR = Set.of(
    "le", "la", "les", "un", "une", "des", "du", "de", ...
);
```

**Impact:** Meilleure UX en contexte francophone

---

### F17: .env.example Absent

**Severite originale:** LOW
**Verdict:** SKIP - POST-MVP

**Analyse:**
- Projet personnel mono-developpeur
- docker-compose.yml (Task 42) documentera les variables
- application.yml avec placeholders = source de verite

**Actions:** Creer .env.example avec Task 42 si desire

**Impact:** Documentation, pas critique

---

### F18: Tests Progress MCP Absents

**Severite originale:** LOW
**Verdict:** REPORTER - POST-MVP

**Analyse:**
- AC 3 exige progress updates mais Task 40 ne les teste pas
- Test E2E des notifications = complexite moderee
- Test unitaire avec mock du contexte suffit

**Actions:**
- Ajouter test unitaire verifiant les appels a `context.progress()`
- Reporter test E2E a v2

**Impact:** Gap AC/tests documente, risque faible

---

## Actions sur l'Implementation-Plan

### Tasks a Modifier

| Task | Modifications |
|------|---------------|
| Task 3 | + `hikari.auto-commit: false`, + `connection-init-sql` HNSW, + `server.shutdown: graceful` |
| Task 7 | + champs `fileSize`, `fileModifiedAt` dans ChunkMetadata |
| Task 8 | + stopwords francais |
| Task 13 | + RateLimiter + Bulkhead (optionnel) |
| Task 21 | + `@Transactional`, + implementer `getStoredDocumentInfo()` |

### Tasks a Creer

| Nouvelle Task | Description |
|---------------|-------------|
| Task 3b | logback-spring.xml simplifie avec correlationId |

### Documentation a Ajouter

| Fichier | Contenu |
|---------|---------|
| chunking-strategy-from-research-15.md | Strategie llms.txt: 1 chunk par H2 |
| notes.md | Risque Testcontainers + Java 25 accepte |

---

## Annexe: Statistiques

| Categorie | Total | Accepter | Reporter | Rejeter |
|-----------|-------|----------|----------|---------|
| CRITICAL | 3 | 1 | 0 | 2 |
| HIGH | 4 | 2 | 1 | 1 |
| MEDIUM | 8 | 1 | 4 | 3 |
| LOW | 3 | 1 | 2 | 0 |
| **TOTAL** | **18** | **5** | **7** | **6** |

---

**Document genere le 2026-01-06 par analyse multi-agents**
