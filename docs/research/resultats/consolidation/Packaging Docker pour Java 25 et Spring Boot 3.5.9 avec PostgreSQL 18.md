# Packaging Docker pour Java 25 + Spring Boot 3.5.9 avec PostgreSQL 18

**Pour Alexandria, l'approche optimale combine un Dockerfile multi-stage avec layered JAR et une image eclipse-temurin:25-jre-noble**, offrant le meilleur compromis entre taille (~150-180 MB), compatibilité et maintenabilité. L'utilisation de Buildpacks est déconseillée pour ce projet self-hosted qui requiert contrôle et transparence du packaging.

Java 25 LTS, publié le **16 septembre 2025**, bénéficie d'un écosystème Docker mature avec des images eclipse-temurin disponibles dans toutes les variantes. PostgreSQL 18.1 avec pgvector 0.8.1 s'intègre parfaitement via l'image officielle `pgvector/pgvector:0.8.1-pg18`. La configuration présentée ici est optimisée pour le hardware cible (Intel i5-4570, 24GB RAM).

---

## Images Docker Java 25 disponibles en janvier 2026

Eclipse Temurin reste la référence pour Java 25 LTS avec une certification TCK complète et des mises à jour trimestrielles. Les images sont disponibles en **multi-architecture** (AMD64, ARM64) depuis novembre 2025.

| Image | Taille compressée | Recommandation |
|-------|-------------------|----------------|
| `eclipse-temurin:25-jre-noble` | ~200-230 MB | ✅ **Production** - glibc, stabilité maximale |
| `eclipse-temurin:25-jre-alpine` | ~65-90 MB | ⚠️ Risques musl libc avec certaines libs natives |
| `gcr.io/distroless/java25-debian13` | ~130-150 MB | Sécurité maximale, debugging difficile |
| `bellsoft/liberica-openjre-debian:25-cds` | ~180-200 MB | Alternative avec CDS pré-configuré |

**Pour Alexandria**, l'image `eclipse-temurin:25-jre-noble` (Ubuntu 24.04 LTS) offre la meilleure compatibilité. L'Intel i5-4570 supporte x86-64-v3, donc toutes les images récentes sont compatibles. Alpine est à éviter si vous utilisez des bibliothèques natives (JDBC drivers, crypto) qui pourraient causer des crashes SIGSEGV avec musl.

---

## Dockerfile multi-stage optimisé pour Alexandria

Ce Dockerfile utilise **l'extraction layered JAR** de Spring Boot 3.5.9 pour optimiser le cache Docker. Les dépendances (rarement modifiées) sont séparées du code applicatif, réduisant drastiquement les temps de rebuild.

```dockerfile
# =============================================================================
# Dockerfile Multi-Stage - Alexandria
# Java 25 LTS + Spring Boot 3.5.9
# Taille finale estimée: 150-180 MB
# =============================================================================

# STAGE 1: Build avec Maven
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /build

# Cache des dépendances Maven (layer stable)
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Build de l'application
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Extraction des layers Spring Boot
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

# STAGE 2: Runtime optimisé
FROM eclipse-temurin:25-jre-noble

LABEL maintainer="alexandria-project"
LABEL description="Alexandria MCP Server - Spring Boot 3.5.9 + Java 25 LTS"

# Création utilisateur non-root (sécurité)
RUN groupadd --system --gid 1001 alexandria && \
    useradd --system --uid 1001 --gid 1001 --no-create-home alexandria

WORKDIR /app

# Création des répertoires avec permissions
RUN mkdir -p logs data config && \
    chown -R alexandria:alexandria /app

# Copie des layers dans l'ordre optimal pour le cache Docker
# 1. Dependencies (change rarement)
COPY --from=builder --chown=alexandria:alexandria /build/extracted/dependencies/ ./
# 2. Spring Boot loader (change très rarement)
COPY --from=builder --chown=alexandria:alexandria /build/extracted/spring-boot-loader/ ./
# 3. Snapshot dependencies (change parfois)
COPY --from=builder --chown=alexandria:alexandria /build/extracted/snapshot-dependencies/ ./
# 4. Application code (change souvent)
COPY --from=builder --chown=alexandria:alexandria /build/extracted/application/ ./

USER alexandria

EXPOSE 8080

# Healthcheck avec Spring Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM optimisée pour containers (24GB RAM disponible)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

**Bénéfice du layered JAR** : lors d'un changement de code, seul le layer `application` (~100 KB) est reconstruit au lieu de l'intégralité du JAR (~20+ MB). Les rebuilds passent de **2-3 minutes à 10-15 secondes**.

---

## Configuration docker-compose pour développement local

Cette configuration orchestre l'application Spring Boot avec PostgreSQL 18 + pgvector 0.8.1, incluant healthchecks, persistance et tuning mémoire adapté aux 24GB RAM disponibles.

```yaml
# docker-compose.yml - Alexandria Development Stack
version: "3.8"

services:
  # =========================================
  # PostgreSQL 18 + pgvector 0.8.1
  # =========================================
  postgres:
    image: pgvector/pgvector:0.8.1-pg18
    container_name: alexandria-db
    restart: unless-stopped
    
    # CRITIQUE: PostgreSQL nécessite plus de shared memory que le défaut Docker (64MB)
    shm_size: 8gb
    
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-alexandria}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-alexandria_secret}
      POSTGRES_DB: ${POSTGRES_DB:-alexandria}
      # IMPORTANT: PGDATA a changé dans PostgreSQL 18
      PGDATA: /var/lib/postgresql/18/docker
    
    ports:
      - "5432:5432"
    
    volumes:
      # Persistance des données
      - postgres_data:/var/lib/postgresql
      # Script d'initialisation pgvector
      - ./init-db:/docker-entrypoint-initdb.d:ro
      # Configuration PostgreSQL personnalisée
      - ./config/postgresql.conf:/etc/postgresql/postgresql.conf:ro
    
    command: postgres -c config_file=/etc/postgresql/postgresql.conf
    
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-alexandria} -d ${POSTGRES_DB:-alexandria}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    
    networks:
      - alexandria-network
    
    deploy:
      resources:
        limits:
          memory: 18G
        reservations:
          memory: 6G

  # =========================================
  # Application Spring Boot (Alexandria)
  # =========================================
  app:
    build:
      context: .
      dockerfile: Dockerfile
    image: alexandria/server:${VERSION:-latest}
    container_name: alexandria-app
    restart: unless-stopped
    
    depends_on:
      postgres:
        condition: service_healthy
    
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-docker}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-alexandria}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-alexandria}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-alexandria_secret}
      # Configuration MCP SSE
      SERVER_PORT: 8080
      TZ: Europe/Paris
    
    ports:
      - "8080:8080"
    
    volumes:
      # Configuration externalisée
      - ./config/application-docker.yml:/app/config/application-docker.yml:ro
      # Logs persistants
      - app_logs:/app/logs
      # Données applicatives
      - app_data:/app/data
    
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    
    networks:
      - alexandria-network
    
    deploy:
      resources:
        limits:
          memory: 4G
        reservations:
          memory: 1G

volumes:
  postgres_data:
    driver: local
  app_logs:
    driver: local
  app_data:
    driver: local

networks:
  alexandria-network:
    driver: bridge
```

---

## Script d'initialisation PostgreSQL avec pgvector

Créer le fichier `./init-db/01-init-pgvector.sql` :

```sql
-- Activation de l'extension pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Vérification de l'installation
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE NOTICE 'pgvector % installé avec succès', 
            (SELECT extversion FROM pg_extension WHERE extname = 'vector');
    ELSE
        RAISE EXCEPTION 'Échec installation pgvector';
    END IF;
END $$;

-- Extension monitoring (optionnel mais recommandé)
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Table exemple pour embeddings (adapter selon besoins Alexandria)
CREATE TABLE IF NOT EXISTS document_embeddings (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    content TEXT,
    embedding vector(1536),  -- Dimension OpenAI ada-002
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Index HNSW pour recherche par similarité cosine
CREATE INDEX IF NOT EXISTS idx_embeddings_hnsw 
ON document_embeddings USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

---

## Configuration PostgreSQL optimisée pour 24GB RAM

Créer le fichier `./config/postgresql.conf` :

```ini
# PostgreSQL 18 - Optimisé pour Alexandria (24GB RAM, 4 cores)
listen_addresses = '*'
max_connections = 100

# Mémoire (calculé pour 24GB)
shared_buffers = 6GB           # 25% de 24GB
effective_cache_size = 18GB    # 75% de 24GB
work_mem = 32MB                # Conservative pour Spring Boot
maintenance_work_mem = 1GB     # Pour CREATE INDEX pgvector

# WAL
wal_buffers = 64MB
min_wal_size = 1GB
max_wal_size = 4GB
checkpoint_completion_target = 0.9

# Parallélisme (i5-4570 = 4 cores)
max_worker_processes = 4
max_parallel_workers_per_gather = 2
max_parallel_workers = 4

# Stockage SSD
random_page_cost = 1.1
effective_io_concurrency = 200

# Logging
log_min_duration_statement = 1000
log_checkpoints = on

# Extensions
shared_preload_libraries = 'pg_stat_statements'
```

---

## Buildpacks vs Dockerfile manuel : recommandation claire

Pour Alexandria, **le Dockerfile manuel est recommandé** sur les Buildpacks Cloud Native pour plusieurs raisons décisives :

| Critère | Dockerfile Manuel | Buildpacks |
|---------|-------------------|------------|
| **Taille image finale** | **~150-180 MB** | ~270-400 MB |
| **Contrôle et transparence** | ✅ Total | ⚠️ Abstrait |
| **Temps de build** | **~2-3 min** | ~5-8 min (initial) |
| **Maintenance** | Manuelle | Auto (rebase) |
| **Dépendances externes** | Aucune | Paketo ecosystem |
| **Debugging** | ✅ Facile | ⚠️ Complexe |

**Quand choisir Buildpacks** : équipes enterprise avec infrastructure CI/CD mature, besoin de rebasing automatique des images de sécurité à grande échelle, et peu de contraintes sur la taille des images.

**Quand choisir Dockerfile** : projets self-hosted, besoin de contrôle fin, optimisation de taille importante, apprentissage/compréhension du packaging.

---

## Stratégie de packaging recommandée pour Alexandria

L'approche **"JAR + Docker"** offre la flexibilité maximale pour un projet self-hosted :

**1. JAR exécutable** (`mvn package`) pour :
- Développement local rapide sans Docker
- Tests et debugging
- Déploiement sur machines avec Java pré-installé

**2. Image Docker** (`docker build`) pour :
- Déploiement production standardisé
- Isolation complète avec PostgreSQL
- Portabilité entre machines

**Commandes de build recommandées** :

```bash
# JAR seul (développement)
./mvnw package -DskipTests
java -jar target/alexandria-*.jar

# Image Docker (production)
docker build -t alexandria/server:1.0.0 .

# Stack complète (développement local)
docker-compose up -d

# Production avec .env personnalisé
docker-compose --env-file .env.prod up -d
```

---

## Estimation des tailles d'images finales

| Configuration | Taille estimée | Notes |
|---------------|----------------|-------|
| App Spring Boot (JAR) | ~20-40 MB | Dépendances incluses |
| **Image Alexandria (recommandée)** | **~150-180 MB** | eclipse-temurin:25-jre-noble + layered JAR |
| Image avec Alpine | ~100-130 MB | Risques compatibilité musl |
| Image avec jlink custom | ~90-120 MB | Complexité accrue, gain marginal |
| PostgreSQL + pgvector | ~400-450 MB | pgvector/pgvector:0.8.1-pg18 |

**Stack complète Alexandria** : ~550-630 MB pour les deux images combinées, ce qui est excellent pour un serveur MCP complet avec base de données vectorielle.

---

## Checklist de mise en œuvre

Pour déployer Alexandria avec cette configuration Docker :

- [ ] Créer la structure de fichiers : `Dockerfile`, `docker-compose.yml`, `init-db/`, `config/`
- [ ] Configurer les variables d'environnement dans `.env` (ne pas commiter les secrets)
- [ ] Activer Spring Actuator dans `application.yml` pour les healthchecks
- [ ] Tester le build local : `docker build -t alexandria/server:dev .`
- [ ] Vérifier la connexion PostgreSQL : `docker-compose up postgres` puis test JDBC
- [ ] Déployer la stack complète : `docker-compose up -d`
- [ ] Monitorer les logs : `docker-compose logs -f app`

Cette configuration fournit une base solide, sécurisée et optimisée pour le projet Alexandria, avec une image légère (~150 MB) et une stack de développement complète prête à l'emploi.