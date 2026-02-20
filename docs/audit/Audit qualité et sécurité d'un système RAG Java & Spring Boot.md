# Audit qualité et sécurité d'un système RAG Java/Spring Boot

**La stack qualité actuelle (JaCoCo, PIT, SpotBugs, SonarCloud, ArchUnit) couvre bien les fondamentaux mais laisse trois angles morts critiques : la nullité à la compilation, les tests du protocole MCP, et le scanning de sécurité des conteneurs.** L'ajout ciblé de 6 outils — Error Prone + NullAway, Trivy, OWASP Dependency-Check, jqwik, Spotless, et un client MCP de test — comblerait ces lacunes en moins de deux semaines de travail solo. L'enjeu principal n'est pas d'empiler les outils mais de maintenir un ratio effort/valeur proportionné à un projet de ~3875 lignes. Ce rapport couvre l'analyse statique avancée, le testing MCP, les tests E2E du pipeline RAG, la sécurité des dépendances ML/ONNX, et fournit un plan d'adoption phasé avec des configurations Gradle concrètes.

---

## 1. Résumé exécutif

### Top 5 actions prioritaires par impact/effort

1. **Error Prone 2.45.0 + NullAway 0.12.15** (3-5h) — Détection de bugs à la compilation que SpotBugs/SonarCloud ne couvrent pas : nullité statique, types incompatibles dans les collections, valeurs de retour ignorées. NullAway est conscient de `@Autowired` Spring. Impact immédiat sur la robustesse du code.
    
2. **Trivy + OWASP Dependency-Check** (1h) — Scanning de sécurité des 3 images Docker et des dépendances Java/ONNX. Trivy couvre conteneurs + filesystem en un seul outil gratuit. Le CVE-2025-28197 (SSRF dans Crawl4AI ≤0.4.247) est un risque réel et immédiat à évaluer.
    
3. **Tests MCP via snapshot + client MCP** (3-4j) — Aucun framework de test MCP dédié n'existe en Java. La stratégie recommandée combine snapshot testing de `tools/list` avec json-unit-assertj et tests d'intégration via `McpAsyncClient` du SDK MCP Java. `@WebMvcTest` n'est **pas** applicable au protocole MCP (JSON-RPC sur SSE, pas REST).
    
4. **jqwik pour le MarkdownChunker** (2-3j) — Le chunker est le composant le plus sensible du pipeline : un split au milieu d'un code block ou d'une table GFM corrompt silencieusement les résultats de recherche. Le property-based testing vérifie des invariants structurels impossibles à couvrir exhaustivement par des tests unitaires classiques.
    
5. **Golden queries pour la non-régression de recherche** (2-3j) — Tests paramétrés JUnit 5 avec un corpus de test ingéré dans Testcontainers pgvector. Détecte automatiquement toute dégradation de pertinence après un changement de chunking, d'embedding ou de reranking.
    

### Gaps critiques dans la stack actuelle

Les trois lacunes les plus importantes sont l'absence totale de **vérification de nullité à la compilation** (SpotBugs et SonarCloud ne font que de l'analyse heuristique post-compilation), l'absence de **tests du protocole MCP** (les 6 outils exposés ne sont testés qu'indirectement via les services sous-jacents), et l'absence de **scanning de vulnérabilités** sur les images Docker et les dépendances ML (ONNX Runtime, Crawl4AI avec Chromium headless).

---

## 2. Tableau comparatif analyse statique

|Outil|Types de bugs détectés|Complémentarité SpotBugs/SonarCloud|Java 21 / Gradle KTS|Effort|Faux positifs|Score /5|Confiance|
|---|---|---|---|---|---|---|---|
|**Error Prone 2.45.0**|Types incompatibles collections, `ReturnValueIgnored`, `StreamResourceLeak`, unicode directionality, autofix Refaster|**Élevée** — analyse compile-time vs bytecode, 400+ patterns dont ~50% absents de SpotBugs|✅ Natif JDK 21, plugin `net.ltgt.errorprone` 4.4.0|1-2h|Faible (ERROR-level très fiable)|**5/5**|Élevé|
|**NullAway 0.12.15**|Null pointer dereference statique, champs non initialisés, propagation nullable|**Très élevée** — seul outil offrant la nullité statique compile-time avec <10% overhead|✅ Plugin Error Prone, `net.ltgt.nullaway` 2.2.0|2-4h|Faible (Spring `@Autowired` supporté nativement)|**5/5**|Élevé|
|**PMD 7.21.0**|Copy-paste (CPD), complexité cyclomatique, design issues, branches identiques|**Modérée** — CPD est unique, le reste chevauche SonarCloud|✅ Plugin Gradle built-in|0.5h|Modéré (règles design opinionnées)|**3/5**|Élevé|
|**Checker Framework 3.53.1**|Nullness sound, Resource Leak, Tainting, Lock ordering, Index bounds|**Élevée** — analyse sound vs optimiste, checkers spécialisés uniques|✅ Plugin `org.checkerframework` 1.0.2|4-8h|Élevé (analyse stricte = plus de bruit)|**2/5**|Élevé|
|**Semgrep CE**|Patterns sécurité custom, Spring security audit, SSRF, injections|**Modérée** — valeur surtout pour règles custom LangChain4j/ONNX|✅ CLI externe, pas de plugin Gradle|1-2h|Faible (pattern matching précis)|**3/5**|Moyen|
|**Qodana Community**|Inspections IntelliJ en CI|**Faible** — redondant avec SpotBugs + SonarCloud + IntelliJ IDE|✅ Docker-based|1-2h|Modéré|**1/5**|Élevé|
|**Modernizer 1.12.0**|APIs Java obsolètes (Vector→ArrayList, Joda→java.time)|**Faible** — peu de trouvailles attendues sur un projet Java 21 neuf|✅ Plugin `com.github.andygoossens.modernizer`|0.5h|Très faible|**2/5**|Élevé|

**Recommandation** : adopter Error Prone + NullAway (Tier 1) et PMD (Tier 2). Le Checker Framework impose un overhead de compilation de **2.8-5.1×** disproportionné pour un développeur solo en itération rapide. Qodana est redondant avec la stack existante.

### Matrice de complémentarité par catégorie de bug

|Catégorie|SpotBugs|SonarCloud|Error Prone|NullAway|PMD|
|---|---|---|---|---|---|
|**Null safety**|Basique|Basique|Partiel|★ **Meilleur**|—|
|**Type safety**|Bytecode|Bon|★ **Meilleur**|—|—|
|**Resource leaks**|Bon|Bon|`StreamResourceLeak`|—|—|
|**Concurrence**|Bon|Bon|Partiel|—|—|
|**Duplication code**|—|CPD intégré|—|—|★ **CPD**|
|**Autofix**|—|—|★ **Refaster rules**|—|—|
|**Spring-spécifique**|Limité|Bon|—|`@Autowired`-aware|—|

---

## 3. Guide testing MCP

### Pourquoi `@WebMvcTest` ne fonctionne pas pour MCP

Spring AI MCP Server WebMVC expose les outils via **SSE (Server-Sent Events) avec JSON-RPC 2.0**, pas via des endpoints REST classiques avec `@RequestMapping`. Les outils standard `MockMvc` et `@WebMvcTest` ne peuvent pas interagir avec le protocole MCP. Il faut utiliser un **client MCP programmatique** pour les tests d'intégration.

Aucun framework de test MCP dédié n'existe en Java à ce jour (février 2026). Le seul outil officiel est le **MCP Inspector** (`@modelcontextprotocol/inspector`), un outil Node.js qui offre un mode CLI exploitable en CI. L'écosystème MCP testing est encore immature.

### Stratégie recommandée en trois couches

**Couche 1 — Snapshot testing du schéma des outils (priorité maximale, ~2h)**

La technique la plus rentable consiste à sérialiser la réponse de `tools/list` et la comparer à un snapshot versionné. Cela détecte toute régression de schéma (paramètres renommés, types modifiés, outils supprimés) sur les 6 outils en un seul test.

Dépendance : `net.javacrumbs.json-unit:json-unit-assertj:3.5.0` (scope test). Le test appelle `mcpClient.listTools()`, sérialise le résultat en JSON, et compare avec `src/test/resources/mcp-tool-schemas.snapshot.json` en ignorant les champs volatils (`cursor`, `timestamp`). La validation JSON Schema avec `com.networknt:json-schema-validator:1.5.4` peut compléter en vérifiant que chaque `inputSchema` est un JSON Schema valide.

**Couche 2 — Tests fonctionnels unitaires des services `@Tool` (~2-3j)**

Les méthodes annotées `@Tool` dans les services Spring sont testables directement avec JUnit 5 + Mockito, sans transport MCP. Pour `search_docs` : mocker le `ContentRetriever` et vérifier le formatage de la réponse, la gestion des requêtes vides, et le respect du token budget. Pour `add_source`/`remove_source` : mocker le repository et vérifier les validations (URL invalide, source inexistante). Cette couche est **la plus rapide à écrire et exécuter**.

**Couche 3 — Tests d'intégration round-trip via MCP client (~3-4j)**

Utiliser `spring-ai-starter-mcp-client-webflux` en scope test pour créer un `McpAsyncClient` qui se connecte au serveur démarré par `@SpringBootTest(webEnvironment = RANDOM_PORT)`. Ce pattern, documenté par Nicklas Wiegandt (janvier 2026), est le seul exemple réel d'intégration testing MCP en Java. Le client appelle `mcpClient.callTool(new McpSchema.CallToolRequest("search_docs", Map.of("query", "spring boot")))` et vérifie la réponse `McpSchema.CallToolResult`.

### Templates de test pour les 6 outils MCP

|Outil|Happy path|Error cases|Assertions clés|
|---|---|---|---|
|`search_docs`|Query valide → résultats formatés|Query vide, aucun résultat|Nombre résultats ≤ limit, token budget ≤ 5000|
|`list_sources`|Sources existantes → liste|Aucune source|Format JSON stable, champs requis présents|
|`add_source`|URL valide → source créée|URL invalide, doublon|Persistance en DB vérifiable via `list_sources`|
|`remove_source`|ID existant → suppression|ID inexistant|Source absente après suppression|
|`crawl_status`|Source en cours → statut|Source inexistante|Champs status, progress, last_crawl présents|
|`recrawl_source`|Source existante → re-crawl lancé|Source inexistante|Statut passe à "crawling"|

### Pact et Spring Cloud Contract ne sont pas applicables

**Pact** et **Spring Cloud Contract** sont conçus pour des APIs REST request-response. Le protocole MCP utilise JSON-RPC 2.0 sur SSE, ce qui ne se mappe pas naturellement sur le modèle de contrat de Pact. L'adaptation nécessiterait des transport handlers custom pour JSON-RPC — un effort disproportionné (~2+ semaines) pour 6 outils. Le snapshot testing de `tools/list` offre **80% de la valeur pour 5% de l'effort**.

### Test du token budget truncation

Le property-based testing avec **jqwik 1.9.3** (`net.jqwik:jqwik:1.9.3`) est la meilleure approche pour la troncation. Les propriétés à vérifier : le résultat tronqué ne dépasse **jamais** 5000 tokens, l'ordre des résultats est préservé, la sortie reste du JSON/Markdown valide, et aucun code block n'est coupé au milieu. Des edge cases explicites doivent couvrir : résultat unique excédant le budget, contenu Unicode, tables Markdown, et le cas exact à la frontière du budget.

---

## 4. Guide tests E2E pipeline RAG

### Architecture de la test suite recommandée

Le pipeline RAG a deux chemins critiques à tester de bout en bout : **l'ingestion** (Crawl4AI → Chunking → Embedding → pgvector) et **la recherche** (query → vector+FTS → RRF → reranking → truncation). Chaque chemin nécessite une stratégie différente.

**Tests d'ingestion pipeline** : un test `@SpringBootTest` avec Testcontainers pgvector charge des fixtures Markdown depuis `src/test/resources/fixtures/`, les passe dans le `MarkdownChunker`, embed les chunks via le modèle ONNX `BgeSmallEnQuantizedEmbeddingModel` (qui tourne in-process sans Docker), et les stocke dans `PgVectorEmbeddingStore`. Les assertions vérifient : nombre de chunks correct, dimension des embeddings = **384**, vecteurs non-nuls, et récupérabilité via similarity search. Le modèle ONNX est **déterministe** : le même texte produit toujours le même embedding, ce qui permet des assertions exactes.

**Tests de recherche avec golden queries** : après ingestion d'un corpus de test (10-20 documents), des tests paramétrés `@ParameterizedTest @CsvFileSource(resources = "/golden-queries.csv")` vérifient que chaque query retourne le document attendu dans le top-K avec un score minimum. Le fichier CSV contient des triplets (query, expectedDocId, minScore). La non-régression se détecte en comparant les résultats actuels à un baseline sérialisé en JSON : si un golden query perd son document attendu du top-K ou si le score moyen baisse de >5%, le test échoue.

### Fixtures et golden data

Créer **5-7 fichiers Markdown** couvrant les cas structurels critiques : prose simple, tables GFM multi-colonnes, code blocks imbriqués (quadruple backticks), hiérarchie de headings mixte, sections vides, et frontmatter YAML. Utiliser du contenu réaliste (extraits de documentation technique) pour des embeddings représentatifs. Stocker les comptages de chunks attendus et les queries de validation associées à chaque fixture.

Pour les golden queries, commencer avec **10-15 paires query/document** couvrant les cas sémantiques importants : recherche exacte, recherche synonymique, recherche multi-concept, et requêtes négatives (aucun résultat attendu).

### Property-based testing du MarkdownChunker avec jqwik

Le chunker est le composant le plus critique à tester car les bugs sont silencieux — un split incorrect ne provoque pas d'erreur mais dégrade la qualité de recherche. **jqwik 1.9.3** (compatible JUnit 5 Platform, Java 21) permet de vérifier quatre invariants structurels :

- **Conservation du contenu** : la concaténation de tous les chunks reproduit le document original (modulo normalisation whitespace)
- **Bornes de taille** : chaque chunk respecte la taille maximum en tokens et n'est jamais vide
- **Intégrité des code blocks** : le nombre de triple-backticks dans chaque chunk est pair (fences équilibrées)
- **Intégrité des tables** : si un chunk contient un `|`, il contient une structure de table complète (header + separator + rows)

Configuration Gradle : ajouter `testImplementation("net.jqwik:jqwik:1.9.3")` et configurer `useJUnitPlatform { includeEngines("junit-jupiter", "jqwik") }`. jqwik est en mode maintenance mais stable.

### Test du sidecar Crawl4AI

Crawl4AI expose une API REST sur le port **11235** (`POST /crawl`, `GET /task/{id}`, `GET /health`). La stratégie recommandée pour un développeur solo est **hybride** :

- **WireMock** (`org.wiremock:wiremock-standalone:3.10.0`) pour les tests rapides : enregistrer des réponses Crawl4AI réelles, les rejouer en test. Couvre le happy path et les cas d'erreur (timeout, 403, réseau coupé) de façon déterministe et sans Docker.
- **Testcontainers GenericContainer** avec `unclecode/crawl4ai:0.8.0` pour un **unique smoke test CI** tagué `@Tag("slow")`. Nécessite `--shm-size=1g` et **4 Go+ RAM** — attention au budget mémoire de 14 Go. Wait strategy : `Wait.forHttp("/health").forPort(11235).withStartupTimeout(Duration.ofMinutes(3))`.

Le vrai conteneur Crawl4AI est **lent à démarrer** (30-60s) et **gourmand en RAM**. Pour un développeur solo, prioriser WireMock pour le cycle quotidien et réserver le test réel pour le CI.

### Test des delta crawls

Trois aspects à vérifier : le hash SHA-256 est **déterministe** (même contenu → même hash), le re-crawl sans changement est **idempotent** (aucun chunk dupliqué, comptage stable), et une modification de contenu déclenche la **suppression des anciens chunks** et l'insertion des nouveaux. Un test doit vérifier qu'un crawl multi-document où seul 1 document change ne ré-embed que ce document (vérifiable via un mock du `EmbeddingModel` comptant les appels).

### Couverture cible par composant

|Composant|Couverture cible|Type de test prioritaire|Effort|
|---|---|---|---|
|MarkdownChunker|**90%+** mutation score|Property-based (jqwik) + unit|2-3j|
|Pipeline ingestion|**Integration path** couvert|@SpringBootTest + Testcontainers|2-3j|
|Recherche hybride + RRF|**Golden queries 100%** pass|@ParameterizedTest|2-3j|
|Token budget truncation|**100%** edge cases|jqwik + unit|1-1.5j|
|Delta crawl|**Idempotence** vérifiée|Integration + unit|1-2j|
|Crawl4AI client|**Résilience** couverte|WireMock + 1 smoke test réel|2-3j|
|Cross-encoder reranking|**Déterminisme** vérifié|Unit (ONNX in-process)|0.5j|

---

## 5. Tableau comparatif sécurité dépendances

|Outil|Couverture Java|Couverture ONNX/ML|Couverture Docker|Intégration Gradle|Coût|Score /5|Confiance|
|---|---|---|---|---|---|---|---|
|**Trivy**|8/10 (JAR + lockfile)|7/10 (OS-level scan)|★ **9/10** (3 images)|CLI + GitHub Action|Gratuit|**5/5**|Élevé|
|**OWASP Dep-Check 12.2.0**|★ **9/10** (NVD direct)|5/10 (CPE matching ambigu pour ML)|—|Plugin `org.owasp.dependencycheck`|Gratuit (NVD API key gratuite requise)|**4/5**|Élevé|
|**Grype**|8/10 (GitHub Advisories)|7/10|8/10|CLI|Gratuit|**4/5**|Élevé|
|**Snyk Free**|8/10|6/10|7/10 (100 tests/mois)|CLI `snyk test --gradle`|Gratuit (400 SCA tests/mois)|**3/5**|Moyen|
|**CycloneDX 3.2.0**|SBOM generation|SBOM generation|—|Plugin `org.cyclonedx.bom`|Gratuit|**4/5**|Élevé|

**Recommandation** : **Trivy + OWASP Dependency-Check** en Tier 1. Trivy est le meilleur outil unique pour scanner les 3 images Docker + le filesystem Java. OWASP Dependency-Check complète avec un scan NVD direct intégré au build Gradle. Ne pas utiliser Grype **et** Trivy — les résultats de vulnérabilités se chevauchent à ~90%. Snyk est utile comme second avis occasionnel.

### Risques de sécurité réels vs théoriques pour cette stack

**Risque RÉEL — Crawl4AI SSRF (CVE-2025-28197)** : vulnérabilité confirmée dans Crawl4AI ≤0.4.247 permettant le scanning de réseaux internes via des URLs non validées. C'est le **risque le plus élevé** de la stack. Mitigation : isoler le conteneur Crawl4AI dans un réseau Docker sans accès au host, valider/whitelister les URLs avant crawl, et vérifier si une version patchée est disponible.

**Risque RÉEL — Chromium headless outdated** : le conteneur Crawl4AI embarque Chromium qui reçoit des CVEs fréquentes et activement exploitées. Mitigation : maintenir l'image Crawl4AI à jour.

**Risque MODÉRÉ — CVEs dans les dépendances transitives Java** : les dépendances transitives de Spring Boot, LangChain4j, et ONNX Runtime accumulent régulièrement des CVEs. Le scanning automatisé est la seule défense viable.

**Risque FAIBLE — ONNX model loading** : le format ONNX est **déclaratif et vérifiable**, contrairement aux formats pickle (PyTorch). Il ne permet pas l'exécution de code arbitraire lors du chargement. Les CVEs connues (CVE-2024-27318, CVE-2025-51480) affectent le toolkit **Python** `onnx`, pas `onnxruntime` Java. Pour des modèles connus téléchargés depuis Hugging Face, le risque est minimal — ajouter une vérification SHA-256 au démarrage suffit.

**Risque FAIBLE — LangChain4j** : contrairement à LangChain Python (CVE-2025-68664 CVSS 9.3, CVE-2024-36480 RCE), **LangChain4j n'a aucun CVE publié** à ce jour. C'est un projet plus jeune et architecturalement différent. Surveiller les GitHub Security Advisories.

**Risque NÉGLIGEABLE — pgvector** : aucune CVE spécifique à l'extension pgvector n'a été trouvée dans NVD ou GitHub Security Advisories. Le CVE-2024-10979 référencé par Snyk concerne PostgreSQL core (PL/Perl), pas pgvector.

### Supply chain : actions concrètes

**Vérification d'intégrité des modèles ONNX** : Hugging Face stocke les SHA-256 via Git LFS pour chaque fichier. Ajouter une vérification au Dockerfile ou au startup Java avec `MessageDigest.getInstance("SHA-256")` comparant le hash calculé au hash attendu hardcodé. Hugging Face ne propose **pas** de signature cryptographique (PGP/Sigstore) des modèles [À VÉRIFIER pour les dernières évolutions 2026].

**Gradle Dependency Verification** : fonctionnalité built-in (`./gradlew --write-verification-metadata pgp,sha256 --export-keys help`), génère `gradle/verification-metadata.xml`. Haute sécurité mais **maintenance significative** à chaque mise à jour de dépendance. À activer après stabilisation des dépendances.

**SBOM avec CycloneDX** : plugin `org.cyclonedx.bom` version 3.2.0 génère un SBOM CycloneDX JSON exploitable par Trivy (`trivy sbom bom.json`) ou Grype. Setup : 15 minutes.

---

## 6. Plan d'adoption priorisé

### Phase 1 — Quick wins en moins de 2 jours

**Action 1.1 — Spotless + google-java-format (1h)**

```kotlin
plugins {
    id("com.diffplug.spotless") version "8.2.1"
}
spotless {
    ratchetFrom("origin/main")
    java {
        googleJavaFormat("1.28.0").reflowLongStrings()
        removeUnusedImports()
        formatAnnotations()
    }
}
tasks.named("check") { dependsOn("spotlessCheck") }
```

Impact : formatage cohérent, zéro effort ongoing.

**Action 1.2 — Error Prone + NullAway (3-5h)**

```kotlin
plugins {
    id("net.ltgt.errorprone") version "4.4.0"
    id("net.ltgt.nullaway") version "2.2.0"
}
dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.45.0")
    errorprone("com.uber.nullaway:nullaway:0.12.15")
    compileOnly("org.jspecify:jspecify:1.0.0")
}
nullaway {
    annotatedPackages.add("com.yourpackage")
}
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        nullaway { error() }
    }
}
```

L'effort principal est l'ajout d'annotations `@Nullable` dans le code existant (~43 fichiers). NullAway traite les champs `@Autowired` comme initialisés par Spring. Exclure les entités JPA avec `ExcludedFieldAnnotations` pour `@jakarta.persistence.Id`.

**Action 1.3 — Trivy baseline scan (30min)**

```bash
# Scanner les 3 images Docker
trivy image myapp:latest
trivy image pgvector/pgvector:pg17
trivy image unclecode/crawl4ai:0.8.0
# Scanner le filesystem Java
trivy fs --scanners vuln .
```

Résultat immédiat : inventaire des CVEs existantes. Intégrer en GitHub Action avec `aquasecurity/trivy-action@0.33.1`.

**Action 1.4 — OWASP Dependency-Check (30min)**

```kotlin
plugins {
    id("org.owasp.dependencycheck") version "12.2.0"
}
dependencyCheck {
    failBuildOnCVSS = 7.0f
    nvd { apiKey = System.getenv("NVD_API_KEY") ?: "" }
}
```

Obtenir la clé NVD API gratuite sur https://nvd.nist.gov/developers/request-an-api-key. Le premier scan télécharge la base NVD (5-20 min avec clé API, 30+ min sans).

**Action 1.5 — CycloneDX SBOM (15min)**

```kotlin
plugins {
    id("org.cyclonedx.bom") version "3.2.0"
}
```

Exécuter `./gradlew cyclonedxDirectBom` pour générer `build/reports/cyclonedx-direct/bom.json`.

### Phase 2 — Tests MCP + E2E pipeline + security scanning (moins d'une semaine)

**Action 2.1 — Snapshot testing MCP (2h)**

Ajouter les dépendances test :

```kotlin
testImplementation("org.springframework.ai:spring-ai-starter-mcp-client-webflux")
testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.5.0")
```

Créer un test `@SpringBootTest(webEnvironment = RANDOM_PORT)` qui instancie un `McpAsyncClient` via `WebClientStreamableHttpTransport`, appelle `listTools()`, et compare le JSON résultant à un snapshot versionné. Ce seul test protège les 6 outils contre les régressions de schéma.

**Action 2.2 — Tests d'intégration MCP round-trip (3-4j)**

Pour chaque outil, écrire un test d'intégration utilisant `mcpClient.callTool(new McpSchema.CallToolRequest(...))` avec Testcontainers pgvector. Couvrir le happy path et 2-3 cas d'erreur par outil. Le `McpAsyncClient` retourne un `McpSchema.CallToolResult` dont on extrait le `TextContent` pour assertions.

**Action 2.3 — jqwik pour MarkdownChunker (2-3j)**

```kotlin
testImplementation("net.jqwik:jqwik:1.9.3")
tasks.test { useJUnitPlatform { includeEngines("junit-jupiter", "jqwik") } }
```

Implémenter les 4 propriétés (conservation contenu, bornes taille, code blocks équilibrés, tables complètes) avec des générateurs custom de documents Markdown.

**Action 2.4 — Golden queries search regression (2-3j)**

Créer un corpus de test de 10-20 documents Markdown, l'ingérer dans Testcontainers pgvector, et écrire 10-15 golden queries en `@ParameterizedTest @CsvFileSource`. Assertions : document attendu dans top-5, score minimum respecté. Taguer `@Tag("search-regression")` pour exécution séparée.

**Action 2.5 — WireMock pour Crawl4AI (1-2j)**

Enregistrer des réponses réelles de l'API Crawl4AI (POST `/crawl`, GET `/task/{id}`), les rejouer via WireMock. Couvrir : crawl réussi, timeout (delay 30s), erreur réseau (CONNECTION_RESET), page inaccessible (403). Un unique smoke test avec le vrai conteneur Crawl4AI tagué `@Tag("slow")`.

### Phase 3 — Analyse statique avancée + optimisation (moins d'un mois)

**Action 3.1 — PMD avec CPD (0.5h)**

```kotlin
plugins { pmd }
pmd {
    toolVersion = "7.21.0"
    isConsoleOutput = true
    ruleSets = listOf(
        "category/java/errorprone.xml",
        "category/java/bestpractices.xml"
    )
}
```

La valeur principale est le **Copy-Paste Detector** (CPD), absent de SpotBugs et SonarCloud. Les règles design/complexity peuvent être bruyantes — commencer avec errorprone + bestpractices seulement.

**Action 3.2 — PIT optimisé (2-3h)**

```kotlin
pitest {
    targetClasses.set(setOf("com.yourpackage.service.*", "com.yourpackage.search.*", "com.yourpackage.crawl.*"))
    mutators.set(setOf("STRONGER"))
    threads.set(Runtime.getRuntime().availableProcessors())
    excludedTestClasses.set(setOf("**IntegrationTest", "**IT"))
    enableDefaultIncrementalAnalysis.set(true)
    mutationThreshold.set(75)
    avoidCallsTo.set(setOf("org.slf4j", "org.apache.logging"))
}
```

**Point critique** : toujours exclure les tests d'intégration (Testcontainers) de PIT — ils sont trop lents et font exploser le temps d'exécution. Le groupe de mutateurs `STRONGER` ajoute des mutations de conditions et valeurs de retour au-delà des défauts. Viser **75% mutation score** initialement, monter à 80% progressivement.

**Action 3.3 — Gradle Dependency Verification (1-2h)**

```bash
./gradlew --write-verification-metadata pgp,sha256 --export-keys help
```

Active après stabilisation des dépendances. Chaque mise à jour nécessite de re-générer le fichier — acceptable si les updates sont peu fréquentes.

**Action 3.4 — Vérification SHA-256 des modèles ONNX (1h)**

Ajouter au Dockerfile ou au code de startup une vérification des hashes SHA-256 des fichiers `bge-small-en-v1.5-q.onnx` et `ms-marco-MiniLM-L-6-v2.onnx` contre des valeurs attendues hardcodées. Récupérer les hashes depuis les pages Hugging Face des modèles.

**Action 3.5 — AI code review (30min)**

Pour un dépôt open-source : **CodeRabbit** gratuit (toutes fonctionnalités Pro) via GitHub OAuth. Pour un dépôt privé : **PR-Agent** (Qodo, open-source) avec clé API OpenAI (~0.01-0.10$/review). Agit comme un "second regard" automatique sur chaque PR.

**Action 3.6 — Isolation réseau Crawl4AI (1h)**

Dans Docker Compose, placer le service Crawl4AI dans un réseau dédié avec accès uniquement au réseau externe, sans accès au service app ni à PostgreSQL. Bloquer l'accès au metadata endpoint (169.254.169.254) et au réseau interne.

---

## 7. Pièges et anti-patterns

### Erreurs courantes dans le testing de systèmes RAG Java

**Ne pas tester la pertinence de la recherche, seulement l'absence d'erreur.** Beaucoup de suites de test RAG vérifient que la recherche retourne des résultats sans vérifier lesquels. Un changement de chunking peut réduire drastiquement la qualité sans déclencher aucun test. Les golden queries avec score minimum sont la meilleure défense.

**Utiliser `@SpringBootTest` partout au lieu du test slicing.** Chaque contexte Spring complet prend 7+ secondes à démarrer. Avec 34 fichiers de test, cela s'accumule vite. Utiliser `@DataJpaTest` pour les repositories, tester les services `@Tool` comme des POJOs avec Mockito, et réserver `@SpringBootTest` aux tests d'intégration E2E.

**Tester les embeddings ONNX avec des assertions exactes sur les vecteurs flottants.** Les embeddings sont déterministes pour un même modèle et runtime, mais une mise à jour de ONNX Runtime ou du modèle peut modifier les valeurs. Tester la **relation** (cosine similarity entre "chien" et "animal" > entre "chien" et "voiture") plutôt que les valeurs absolues.

### Outils qui semblent pertinents mais ne le sont pas

**Checker Framework** : l'overhead de compilation de **2.8-5.1×** est rédhibitoire pour un développeur solo en itération rapide sur ~3875 lignes. NullAway offre 80% de la valeur (nullité) pour 10% du coût. Le Checker Framework se justifie pour des projets >50K lignes avec plusieurs développeurs où la soundness formelle est requise.

**Qodana Community** : redondant à >90% avec SpotBugs + SonarCloud + les inspections IntelliJ IDEA que vous utilisez déjà en développement. Le seul avantage (baseline/quality gate) est couvert par SonarCloud.

**Pact / Spring Cloud Contract pour MCP** : le protocole MCP utilise JSON-RPC 2.0 sur SSE, pas REST. Adapter Pact nécessiterait des custom transport handlers — effort disproportionné (~2+ semaines) pour 6 outils. Le snapshot testing de `tools/list` est bien plus pragmatique.

**Schemathesis** : conçu pour OpenAPI/Swagger, inapplicable au protocole MCP.

**Un framework de pre-commit dédié (pre-commit Python)** : ajoute une dépendance Python et une couche de complexité. Pour un développeur solo, `spotlessCheck` dans le build Gradle + CI est suffisant et plus simple.

### Over-engineering à éviter

Pour un projet de **3875 lignes / 43 fichiers / développeur solo**, le risque principal est de passer plus de temps à configurer des outils qu'à développer. Quelques garde-fous :

- **Ne pas dépasser 6-8 plugins Gradle** de qualité. Au-delà, le temps de build et la maintenance des configurations deviennent un frein. La stack recommandée (Error Prone, NullAway, PMD, PIT, Spotless, OWASP DC, CycloneDX, JaCoCo) totalise 8 plugins — c'est le maximum raisonnable.
- **Ne pas écrire de règles Semgrep custom** tant que les outils intégrés (Error Prone, SonarCloud) ne sont pas pleinement exploités. Les règles custom ont un coût de maintenance élevé pour un bénéfice marginal à cette échelle.
- **Ne pas signer les images Docker** avec Cosign pour un déploiement Docker Compose local. C'est pertinent pour une distribution via registry public, pas pour un self-hosted à 3 services.
- **Ne pas activer Gradle Dependency Verification avant la stabilisation des dépendances**. Chaque `dependencyUpdate` nécessite une re-génération manuelle du fichier de vérification — frustrant en phase de développement actif.
- **Le temps total d'exécution de la suite de tests doit rester sous 5 minutes** en local. Au-delà, le feedback loop est trop lent pour un développeur solo. Séparer clairement les tests rapides (unit + slices, <30s) des tests lents (Testcontainers, <3min) avec des tags JUnit 5.

---

## Conclusion

La stack qualité de ce projet RAG est déjà **au-dessus de la moyenne** pour un projet solo de cette taille — JaCoCo à 75%+, PIT, SpotBugs, SonarCloud, ArchUnit, et Testcontainers forment une base solide. Les gains marginaux les plus importants viennent maintenant de trois axes : la **nullité statique à la compilation** (Error Prone + NullAway détectent une classe entière de bugs invisibles à SpotBugs), le **testing du protocole MCP** (aucun outil dédié n'existe, mais le snapshot testing + MCP client offrent une couverture pragmatique), et le **scanning de sécurité des conteneurs** (Trivy en une commande révèle les CVEs des 3 images Docker). Le property-based testing du MarkdownChunker avec jqwik est un investissement de 2-3 jours qui protège le composant le plus critique du pipeline — celui dont les bugs dégradent silencieusement la qualité de recherche sans lever d'erreur. L'ensemble du plan d'adoption tient en ~15-20 jours de travail étalés sur un mois, avec des quick wins dès les premières heures.