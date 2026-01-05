# PGDATA path changed in PostgreSQL 18 Docker images

**The correct PGDATA path for `pgvector/pgvector:0.8.x-pg18` is `/var/lib/postgresql/18/docker`**, not the legacy `/var/lib/postgresql/data` path. This represents a breaking change introduced in PostgreSQL 18's official Docker image in June 2025. Since pgvector simply extends the official PostgreSQL image without modifying PGDATA, it inherits this new versioned path structure automatically.

## PostgreSQL 18 introduced version-specific data directories

The official PostgreSQL Docker image changed its PGDATA convention starting with version 18. This affects all images that extend it, including pgvector.

| PostgreSQL Version | PGDATA Path | Volume Mount Point |
|-------------------|-------------|--------------------|
| **17 and below** | `/var/lib/postgresql/data` | `/var/lib/postgresql/data` |
| **18 and above** | `/var/lib/postgresql/18/docker` | `/var/lib/postgresql` |

The pgvector Dockerfile is minimal—it inherits from `postgres:$PG_MAJOR-$DEBIAN_CODENAME`, installs the vector extension, and makes no PGDATA modifications. This means all volume and path behavior comes directly from the base PostgreSQL image.

## Correct volume configuration for Alexandria deployment

For your self-hosted RAG server using pgvector 0.8.1 with PostgreSQL 18.1, mount volumes at the **parent directory** `/var/lib/postgresql`:

```yaml
# docker-compose.yml for pgvector with PostgreSQL 18+
services:
  alexandria-db:
    image: pgvector/pgvector:0.8.0-pg18
    environment:
      POSTGRES_USER: alexandria
      POSTGRES_PASSWORD: your_secure_password
      POSTGRES_DB: alexandria
    ports:
      - "5432:5432"
    volumes:
      - pgvector_data:/var/lib/postgresql  # NOT /var/lib/postgresql/data

volumes:
  pgvector_data:
```

The container automatically creates data at `/var/lib/postgresql/18/docker` inside this volume. Future PostgreSQL versions will use their respective major version (e.g., `/var/lib/postgresql/19/docker` for PostgreSQL 19).

## Why PostgreSQL changed the PGDATA structure

Three factors drove this architectural change. First, **alignment with ecosystem standards**—the new path `/var/lib/postgresql/MAJOR/docker` matches the `pg_ctlcluster`/`postgresql-common` convention used by Debian and Ubuntu PostgreSQL packages. Second, **easier major version upgrades**—the new structure enables `pg_upgrade --link` (fast upgrade mode) without mount point boundary issues that previously required complex bind mount workarounds. Third, **multi-version coexistence**—versioned subdirectories allow PostgreSQL 18 and 19 data directories to exist in the same volume simultaneously during migrations.

## Using the legacy path structure (if needed)

If your existing setup relies on `/var/lib/postgresql/data`, you can force backward compatibility by explicitly setting PGDATA:

```yaml
services:
  alexandria-db:
    image: pgvector/pgvector:0.8.0-pg18
    environment:
      POSTGRES_USER: alexandria
      POSTGRES_PASSWORD: your_secure_password
      POSTGRES_DB: alexandria
      PGDATA: /var/lib/postgresql/data  # Force legacy behavior
    volumes:
      - pgvector_data:/var/lib/postgresql/data
```

However, this approach sacrifices the `pg_upgrade --link` benefits and may complicate future major version migrations.

## Critical production deployment notes

**Data migration required**: Existing volumes from PostgreSQL 17 using `/var/lib/postgresql/data` cannot be directly mounted with PostgreSQL 18's default configuration. Use `pg_dump`/`pg_restore` or `pg_upgrade` for migration.

**Kubernetes deployments**: If your persistent volume creates a `lost+found` directory (common with Azure disks and some storage backends), set PGDATA to a subdirectory to avoid initialization failures:

```yaml
env:
  - name: PGDATA
    value: /var/lib/postgresql/data/pgdata
volumeMounts:
  - mountPath: /var/lib/postgresql/data
    name: alexandria-storage
```

**Bind mounts**: For local development with bind mounts, ensure the host directory exists and has appropriate permissions:

```bash
docker run -d \
  -v /opt/alexandria/pgdata:/var/lib/postgresql \
  -e POSTGRES_PASSWORD=secret \
  pgvector/pgvector:0.8.0-pg18
```

## Conclusion

For `pgvector/pgvector:0.8.x-pg18`, the answer is definitively **`/var/lib/postgresql/18/docker`** for PGDATA, with volumes mounted at `/var/lib/postgresql`. This version-specific path structure is inherited from the official PostgreSQL 18 Docker image and enables streamlined major version upgrades. For production Alexandria deployments, use the new mount point unless backward compatibility with existing data directories is essential.