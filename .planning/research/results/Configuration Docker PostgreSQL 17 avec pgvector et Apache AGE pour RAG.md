# Configuration Docker PostgreSQL 17 avec pgvector et Apache AGE pour RAG

La combinaison de **pgvector 0.8.1** et **Apache AGE 1.6.0** sur **PostgreSQL 17** est pleinement supportée et fonctionnelle. Aucune image Docker préexistante ne contient les deux extensions ; un Dockerfile personnalisé basé sur `pgvector/pgvector:pg17` est requis pour ajouter Apache AGE via compilation.

## Compatibilité des versions confirmée

Les deux extensions sont officiellement compatibles avec PostgreSQL 17 :

| Extension | Version | Support PG17 | Source |
|-----------|---------|--------------|--------|
| **pgvector** | 0.8.1 | ✅ Natif | Image Docker officielle `pgvector/pgvector:pg17` |
| **Apache AGE** | 1.6.0-rc0 | ✅ Officiel | Tag Git `PG17/v1.6.0-rc0` (22 sept 2024) |

**Contrainte importante** : Apache AGE 1.6.0 pour PostgreSQL 17 n'a pas de script de mise à niveau depuis les versions précédentes. Les deux extensions coexistent sans conflit car elles opèrent sur des types de données distincts (vecteurs vs graphes).

## Dockerfile complet avec les deux extensions

Ce Dockerfile part de l'image officielle pgvector et compile Apache AGE :

```dockerfile
# Dockerfile pour PostgreSQL 17 + pgvector 0.8.1 + Apache AGE 1.6.0
FROM pgvector/pgvector:0.8.1-pg17

USER root

# Dépendances de compilation pour Apache AGE
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libreadline-dev \
    zlib1g-dev \
    flex \
    bison \
    git \
    postgresql-server-dev-17 \
    && rm -rf /var/lib/apt/lists/*

# Compilation et installation d'Apache AGE 1.6.0 pour PostgreSQL 17
WORKDIR /tmp
RUN git clone --branch PG17/v1.6.0-rc0 --depth 1 https://github.com/apache/age.git && \
    cd age && \
    make PG_CONFIG=/usr/lib/postgresql/17/bin/pg_config && \
    make install PG_CONFIG=/usr/lib/postgresql/17/bin/pg_config && \
    cd .. && \
    rm -rf age

# Nettoyage des dépendances de build pour réduire la taille de l'image
RUN apt-get purge -y --auto-remove \
    build-essential \
    git \
    postgresql-server-dev-17 \
    flex \
    bison && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Retour à l'utilisateur postgres
USER postgres

# Copie de la configuration personnalisée (optionnel)
# COPY postgresql.conf /etc/postgresql/postgresql.conf
```

## Configuration docker-compose.yml complète

```yaml
version: '3.8'

services:
  postgres-rag:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: postgres-rag-db
    restart: unless-stopped
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-raguser}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-ragpassword}
      POSTGRES_DB: ${POSTGRES_DB:-ragdb}
    ports:
      - "5432:5432"
    volumes:
      # Persistance des données
      - postgres_data:/var/lib/postgresql/data
      # Scripts d'initialisation (exécutés une seule fois)
      - ./init:/docker-entrypoint-initdb.d:ro
      # Configuration PostgreSQL personnalisée
      - ./postgresql.conf:/etc/postgresql/postgresql.conf:ro
    # Mémoire partagée pour les index HNSW parallèles
    shm_size: 1g
    # Configuration PostgreSQL via ligne de commande
    command: >
      postgres 
      -c config_file=/etc/postgresql/postgresql.conf
      -c shared_preload_libraries=age
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  # Service Java LangChain4j (exemple)
  langchain4j-app:
    build:
      context: ./app
      dockerfile: Dockerfile
    container_name: rag-java-app
    depends_on:
      postgres-rag:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-rag:5432/${POSTGRES_DB:-ragdb}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-raguser}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-ragpassword}
    ports:
      - "8080:8080"

volumes:
  postgres_data:
    driver: local
```

## Script d'initialisation init.sql

Créez le fichier `init/001_extensions.sql` :

```sql
-- ============================================
-- Initialisation des extensions PostgreSQL
-- pour système RAG avec pgvector + Apache AGE
-- ============================================

-- Extension pgvector pour embeddings vectoriels
-- (pas besoin de LOAD, fonctionne immédiatement)
CREATE EXTENSION IF NOT EXISTS vector;

-- Extension Apache AGE pour base de données graphe
-- (requiert shared_preload_libraries='age')
CREATE EXTENSION IF NOT EXISTS age;

-- Chargement d'AGE pour cette session
LOAD 'age';

-- Configuration du search_path pour inclure ag_catalog
ALTER DATABASE ragdb SET search_path = ag_catalog, "$user", public;

-- Vérification des extensions installées
DO $$
BEGIN
    RAISE NOTICE 'Extensions installées:';
    RAISE NOTICE '  - pgvector: %', (SELECT extversion FROM pg_extension WHERE extname = 'vector');
    RAISE NOTICE '  - Apache AGE: %', (SELECT extversion FROM pg_extension WHERE extname = 'age');
END $$;

-- ============================================
-- Schéma pour le système RAG
-- ============================================

-- Table pour stocker les documents et leurs embeddings
CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    embedding vector(1536),  -- Dimension pour OpenAI ada-002 / text-embedding-3-small
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index HNSW pour recherche de similarité rapide
CREATE INDEX IF NOT EXISTS documents_embedding_idx 
ON documents USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Index GIN pour recherche dans les métadonnées
CREATE INDEX IF NOT EXISTS documents_metadata_idx 
ON documents USING GIN (metadata);

-- ============================================
-- Graphe Apache AGE pour relations sémantiques
-- ============================================

-- Création du graphe de connaissances
SELECT create_graph('knowledge_graph');

-- Exemple de création de nœuds et relations (à adapter selon vos besoins)
-- SELECT * FROM cypher('knowledge_graph', $$
--     CREATE (d:Document {name: 'example', type: 'pdf'})
--     RETURN d
-- $$) AS (d agtype);
```

## Configuration PostgreSQL optimisée pour RAG

Créez le fichier `postgresql.conf` :

```ini
# ============================================
# Configuration PostgreSQL pour RAG
# pgvector + Apache AGE - Environnement Dev
# ============================================

# Connexions
listen_addresses = '*'
max_connections = 100

# ============================================
# Mémoire - Configuration développement
# Ajuster selon la RAM disponible
# ============================================

# 25% de la RAM disponible (256MB pour 1GB RAM)
shared_buffers = 256MB

# Cache effectif pour le planificateur (50-75% RAM)
effective_cache_size = 1GB

# CRITIQUE pour pgvector: mémoire pour construction d'index HNSW
# Si l'index dépasse cette valeur, les performances chutent de 10x+
# Recommandation: 50-60% de la RAM pour builds intensifs
maintenance_work_mem = 512MB

# Mémoire par opération de tri/hash
work_mem = 16MB

# ============================================
# Parallélisme - Important pour pgvector
# ============================================

max_worker_processes = 8
max_parallel_workers = 8
max_parallel_workers_per_gather = 4
max_parallel_maintenance_workers = 4

# ============================================
# Extensions
# ============================================

# Apache AGE REQUIERT cette configuration
# pgvector n'en a PAS besoin
shared_preload_libraries = 'age'

# Search path pour Apache AGE
search_path = 'ag_catalog, "$user", public'

# ============================================
# Write-Ahead Log (WAL)
# ============================================

wal_level = replica
max_wal_size = 1GB
min_wal_size = 80MB

# ============================================
# Logging
# ============================================

logging_collector = on
log_destination = 'stderr'
log_directory = 'pg_log'
log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'
log_statement = 'ddl'
log_duration = off

# ============================================
# Performance des index vectoriels
# ============================================

# Désactiver JIT si problèmes de performance avec pgvector
# jit = off

# Pour les grandes requêtes vectorielles
effective_io_concurrency = 200
random_page_cost = 1.1
```

## Structure du projet recommandée

```
project/
├── docker-compose.yml
├── Dockerfile
├── .env                    # Variables d'environnement
├── postgresql.conf
├── init/
│   └── 001_extensions.sql
└── app/
    ├── Dockerfile          # Application Java
    └── src/
```

Fichier `.env` :

```env
POSTGRES_USER=raguser
POSTGRES_PASSWORD=your_secure_password_here
POSTGRES_DB=ragdb
```

## Notes sur Apache AGE et les limitations découvertes

Apache AGE 1.6.0 pour PostgreSQL 17 présente quelques particularités à connaître. Le tag de release porte le suffixe `-rc0` mais représente bien la version stable actuelle. **Aucun script de migration** n'existe depuis les versions antérieures de AGE, ce qui signifie qu'une installation fraîche est nécessaire plutôt qu'une mise à niveau.

Chaque session PostgreSQL utilisant AGE doit exécuter `LOAD 'age'` et configurer le `search_path` pour inclure `ag_catalog`. La configuration `shared_preload_libraries = 'age'` dans `postgresql.conf` permet de charger AGE au démarrage du serveur mais ne dispense pas du `LOAD` par session pour les requêtes Cypher.

Pour les utilisateurs non-superusers, un symlink est requis :
```bash
ln -s /usr/lib/postgresql/17/lib/age.so /usr/lib/postgresql/17/lib/plugins/age.so
```

## Recommandations mémoire selon l'environnement

| Paramètre | Dev (4GB RAM) | Prod (16GB RAM) | Usage |
|-----------|---------------|-----------------|-------|
| `shared_buffers` | 256MB | 4GB | Cache des pages |
| `effective_cache_size` | 1GB | 12GB | Hint planificateur |
| `maintenance_work_mem` | 512MB | 2GB | **Construction index HNSW** |
| `work_mem` | 16MB | 64MB | Tris et hash par opération |
| `shm_size` (Docker) | 512MB | 2GB | Doit être ≥ maintenance_work_mem |

Le paramètre `maintenance_work_mem` est **critique pour pgvector** : si la construction d'un index HNSW nécessite plus de mémoire que cette valeur, les performances chutent drastiquement (10x+ plus lent) à cause des écritures disque temporaires.

## Commandes de démarrage et vérification

```bash
# Construction et démarrage
docker-compose up -d --build

# Vérification des logs
docker-compose logs -f postgres-rag

# Test de connexion et vérification des extensions
docker exec -it postgres-rag-db psql -U raguser -d ragdb -c "
SELECT extname, extversion FROM pg_extension 
WHERE extname IN ('vector', 'age');
"

# Test pgvector
docker exec -it postgres-rag-db psql -U raguser -d ragdb -c "
SELECT '[1,2,3]'::vector <-> '[4,5,6]'::vector AS distance;
"

# Test Apache AGE
docker exec -it postgres-rag-db psql -U raguser -d ragdb -c "
LOAD 'age';
SET search_path = ag_catalog, public;
SELECT * FROM ag_catalog.ag_graph;
"
```

## Conclusion

Cette configuration combine avec succès **pgvector 0.8.1** et **Apache AGE 1.6.0** sur **PostgreSQL 17** via un Dockerfile personnalisé basé sur l'image officielle pgvector. Les deux extensions sont officiellement compatibles et coexistent sans conflit, permettant de construire un système RAG hybride exploitant à la fois les embeddings vectoriels (via LangChain4j) et les relations graphes sémantiques.

Les points d'attention principaux sont : la configuration obligatoire de `shared_preload_libraries` pour AGE, le dimensionnement de `maintenance_work_mem` pour les index HNSW volumineux, et l'ajustement de `shm_size` dans Docker pour correspondre à cette valeur mémoire.