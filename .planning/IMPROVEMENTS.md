# Améliorations post-review — phase-3/web-crawling

PR: https://github.com/sebc-dev/alexandria/pull/9
Review: 19 fichiers, 45 🟢, 30 🟡, 0 🔴
Progression: **8/15 complétées**

---

## Robustesse production (priorité haute)

- [x] **1. Retry avec backoff sur Crawl4AiClient.crawl()**
  - Fichiers: `Crawl4AiClient.java`, `application.yml`
  - Les erreurs transitoires (timeout Chromium) échouent directement
  - Approche: Spring Retry ou retry manuel avec backoff exponentiel

- [x] **2. Limiter la taille des sitemaps téléchargés**
  - Fichier: `SitemapParser.java`
  - `body(byte[].class)` charge tout en RAM, risque OOM sur sitemaps géants
  - Approche: Limiter la taille du body ou utiliser du streaming

- [x] **3. Singleton Container pattern pour les tests IT**
  - Fichiers: `Crawl4AiClientIT.java`, `CrawlServiceIT.java`
  - Deux containers Crawl4AI (~4 Go RAM) démarrés en parallèle
  - Approche: Créer un `AbstractCrawl4AiIT` ou un singleton container

## Qualité de code (priorité moyenne)

- [x] **4. @JsonNaming(SnakeCaseStrategy.class) sur les DTOs Crawl4AI**
  - Fichiers: `Crawl4AiRequest`, `Crawl4AiMarkdown`, `Crawl4AiPageResult`
  - Résout le snake_case de manière centralisée au lieu de noms de champs non-Java

- [x] **5. @ConfigurationProperties record au lieu de @Value**
  - Fichier: `Crawl4AiConfig.java`
  - Validation au boot, auto-complétion IDE, `/actuator/configprops`

- [x] **6. Factoriser normalizeToBase dans UrlNormalizer**
  - Fichiers: `SitemapParser.java`, `UrlNormalizer.java`
  - Duplication de logique scheme+host+port

- [x] **7. Extraire helper extractMarkdown() dans Crawl4AiClient**
  - Fichier: `Crawl4AiClient.java`
  - Ternaire imbriquée difficile à lire

- [x] **8. Factoriser setup Testcontainers Crawl4AI**
  - Fichiers: `Crawl4AiClientIT.java`, `CrawlServiceIT.java`
  - Copié-collé + image `0.8.0` dupliquée à 3 endroits (docker-compose + 2 tests)

## Complétude (priorité basse)

- [ ] **9. Tri alphabétique des query params dans UrlNormalizer**
  - Fichier: `UrlNormalizer.java`
  - `?a=1&b=2` vs `?b=2&a=1` produit deux URLs distinctes

- [ ] **10. Enrichir les tests unitaires UrlNormalizer**
  - Fichier: `UrlNormalizerTest.java`
  - Manque: port défaut (443→omis), query params mixtes, null/blank, ports différents dans isSameSite

- [ ] **11. Collecter les pages en échec dans CrawlService**
  - Fichier: `CrawlService.java`
  - Retourner `CrawlSiteResult(successPages, failedUrls)` pour le monitoring

- [ ] **12. Crawl concurrent dans CrawlService**
  - Fichier: `CrawlService.java`
  - Séquentiel actuellement, lent pour 500+ pages

## Cosmétique (nice to have)

- [ ] **13. Regrouper dépendances par domaine dans build.gradle.kts**
- [ ] **14. Port Crawl4AI optionnel dans docker-compose.yml pour debug local**
- [ ] **15. Retirer @JsonIgnoreProperties de Crawl4AiRequest** (inutile sur un objet sérialisé)
