package dev.alexandria.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Configuration de test pour PostgreSQL avec pgvector.
 *
 * <p>Utilise Testcontainers 2.x avec l'image pgvector officielle. Le conteneur est partagé entre
 * tous les tests via @ServiceConnection (auto-configuration Spring Boot).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PgVectorTestConfiguration {

  /** Image Docker officielle pgvector avec PostgreSQL 18. */
  public static final String PGVECTOR_IMAGE = "pgvector/pgvector:0.8.1-pg18";

  /**
   * Crée un conteneur PostgreSQL avec pgvector pour les tests.
   *
   * <p>Le conteneur est automatiquement connecté à Spring Boot via @ServiceConnection.
   *
   * @return conteneur PostgreSQL configuré
   */
  @Bean
  @ServiceConnection
  public PostgreSQLContainer postgresContainer() {
    DockerImageName imageName =
        DockerImageName.parse(PGVECTOR_IMAGE).asCompatibleSubstituteFor("postgres");
    return new PostgreSQLContainer(imageName).withReuse(true);
  }
}
