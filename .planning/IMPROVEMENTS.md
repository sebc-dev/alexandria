# Améliorations post-review — phase-3/web-crawling

PR: https://github.com/sebc-dev/alexandria/pull/9
Review: 19 fichiers, 45 🟢, 30 🟡, 0 🔴
Progression: **15/15 complétées**

---

## Robustesse production (priorité haute)

- [x] **1. Retry avec backoff sur Crawl4AiClient.crawl()**
  - Fichiers: `Crawl4AiClient.java`, `Crawl4AiConfig.java`, `Crawl4AiProperties.java`, `application.yml`, `build.gradle.kts`, `libs.versions.toml`
  - Ajouté `spring-retry` + `spring-boot-starter-aop`
  - `@Retryable` sur `crawl()` (retryFor=RestClientException, config via SpEL expressions)
  - `@Recover` méthode `recoverCrawl()` pour le fallback gracieux
  - Config: `alexandria.crawl4ai.retry.{max-attempts,delay-ms,multiplier}` (3, 1000, 2.0)

- [x] **2. Limiter la taille des sitemaps téléchargés**
  - Fichiers: `SitemapParser.java`, `Crawl4AiProperties.java`, `application.yml`
  - Nouvelle méthode `fetchSitemap()` avec vérification de taille après téléchargement
  - Limite configurable: `alexandria.crawl4ai.max-sitemap-size-bytes` (défaut 10 Mo)
  - Log warning et retour null si dépassée

- [x] **3. Singleton Container pattern pour les tests IT**
  - Fichiers: `SharedCrawl4AiContainer.java` (nouveau), `Crawl4AiClientIT.java`, `CrawlServiceIT.java`
  - Singleton statique `INSTANCE` avec image centralisée en constante
  - Méthode `baseUrl()` réutilisée via `@DynamicPropertySource`
  - Les deux IT partagent un seul container au lieu de deux

## Qualité de code (priorité moyenne)

- [x] **4. @JsonNaming(SnakeCaseStrategy.class) sur les DTOs Crawl4AI**
  - Fichiers: `Crawl4AiRequest.java`, `Crawl4AiMarkdown.java`, `Crawl4AiPageResult.java`, `Crawl4AiClient.java`
  - `browser_config` → `browserConfig`, `crawler_config` → `crawlerConfig`
  - `raw_markdown` → `rawMarkdown`, `fit_markdown` → `fitMarkdown`, etc.
  - `status_code` → `statusCode`, `error_message` → `errorMessage`
  - Supprimé `@JsonIgnoreProperties` inutile sur `Crawl4AiRequest` (cf. #15)

- [x] **5. @ConfigurationProperties record au lieu de @Value**
  - Fichiers: `Crawl4AiProperties.java` (nouveau), `Crawl4AiConfig.java`
  - Record `Crawl4AiProperties` avec `@ConfigurationProperties(prefix = "alexandria.crawl4ai")`
  - Nested record `Retry` pour la config retry
  - `@EnableConfigurationProperties` sur `Crawl4AiConfig`

- [x] **6. Factoriser normalizeToBase dans UrlNormalizer**
  - Fichiers: `UrlNormalizer.java`, `SitemapParser.java`
  - Nouvelle méthode publique `UrlNormalizer.normalizeToBase(String)`
  - `SitemapParser.normalizeToBase()` supprimée, remplacée par appel à `UrlNormalizer`
  - `isSameSite()` refactoré pour utiliser `normalizeToBase()`
  - `effectivePort()` supprimée (devenue inutile)

- [x] **7. Extraire helper extractMarkdown() dans Crawl4AiClient**
  - Fichier: `Crawl4AiClient.java`
  - Ternaire imbriquée extraite en méthode privée `extractMarkdown(Crawl4AiMarkdown)`
  - Logique claire : null → null, fitMarkdown non-blank → fitMarkdown, sinon rawMarkdown

- [x] **8. Factoriser setup Testcontainers Crawl4AI**
  - Fichiers: `SharedCrawl4AiContainer.java` (nouveau), `Crawl4AiClientIT.java`, `CrawlServiceIT.java`
  - Combiné avec #3 — image `unclecode/crawl4ai:0.8.0` centralisée en constante `IMAGE`
  - Config partagée : healthcheck, startup timeout 120s, shmSize 1 Go

## Complétude (priorité basse)

- [x] **9. Tri alphabétique des query params dans UrlNormalizer**
  - Fichier: `UrlNormalizer.java`
  - `.sorted()` ajouté dans `filterQueryParams` pour normaliser l'ordre des paramètres

- [x] **10. Enrichir les tests unitaires UrlNormalizer**
  - Fichier: `UrlNormalizerTest.java`
  - Restructuré en `@Nested` classes (Normalize, NormalizeToBase, IsSameSite)
  - Ajouté: port défaut omis (80/443), port non-défaut conservé, null/blank, tri alphabétique params, params mixtes tracking+utiles, ports différents dans isSameSite, URL sans schéma, malformed dans normalizeToBase

- [x] **11. Collecter les pages en échec dans CrawlService**
  - Fichiers: `CrawlSiteResult.java` (nouveau), `CrawlService.java`, `CrawlServiceIT.java`
  - Record `CrawlSiteResult(successPages, failedUrls)` remplace `List<CrawlResult>`
  - Les échecs (crawl failed + exceptions) sont collectés dans `failedUrls`

- [x] **12. Crawl concurrent dans CrawlService**
  - Fichiers: `CrawlService.java`, `Crawl4AiProperties.java`, `application.yml`
  - Crawl par batch avec virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`)
  - Concurrence configurable: `alexandria.crawl4ai.crawl-concurrency` (défaut 4)
  - Compatible BFS link-crawl (URLs découverts ajoutés entre les batchs)

## Cosmétique (nice to have)

- [x] **13. Regrouper dépendances par domaine dans build.gradle.kts**
  - Sections: Spring Boot, AI/Embeddings, Web Crawling, Database, Testing
- [x] **14. Port Crawl4AI optionnel dans docker-compose.yml pour debug local**
  - Port 11235 commenté avec instruction pour activer
- [x] **15. Retirer @JsonIgnoreProperties de Crawl4AiRequest** (fait avec #4 — remplacé par `@JsonNaming`)
