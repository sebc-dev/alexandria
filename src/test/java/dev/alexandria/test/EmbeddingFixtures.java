package dev.alexandria.test;

import java.util.Arrays;
import java.util.Random;

/**
 * Fixtures pour générer des vecteurs d'embedding 1024D pour les tests.
 *
 * <p>Tous les vecteurs sont normalisés L2 (norme euclidienne = 1).
 */
public final class EmbeddingFixtures {

  /** Dimension standard des embeddings Infinity. */
  public static final int EMBEDDING_DIMENSION = 1024;

  /**
   * Seuil de similarité au-delà duquel on retourne directement le vecteur base. Évite les
   * instabilités numériques de Gram-Schmidt quand le vecteur orthogonal devient quasi-nul.
   */
  private static final double SIMILARITY_IDENTITY_THRESHOLD = 0.9999;

  private EmbeddingFixtures() {}

  /**
   * Génère un vecteur d'embedding déterministe basé sur une seed.
   *
   * @param seed seed pour le générateur aléatoire (reproductibilité)
   * @return vecteur 1024D normalisé L2
   */
  public static float[] generate(long seed) {
    Random random = new Random(seed);
    float[] embedding = new float[EMBEDDING_DIMENSION];

    for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
      embedding[i] = random.nextFloat() * 2 - 1; // [-1, 1]
    }

    return normalize(embedding);
  }

  /**
   * Génère un vecteur avec une similarité cosinus contrôlée par rapport à un vecteur de base.
   *
   * <p>Utilise la formule: result = similarity * base + sqrt(1 - similarity²) * orthogonal
   *
   * @param base vecteur de référence (sera normalisé si nécessaire)
   * @param similarity similarité cosinus cible [0, 1]
   * @return vecteur 1024D avec la similarité cosinus spécifiée par rapport à base
   * @throws IllegalArgumentException si similarity n'est pas dans [0, 1]
   */
  public static float[] similar(float[] base, double similarity) {
    if (similarity < 0 || similarity > 1) {
      throw new IllegalArgumentException("Similarity must be in [0, 1], got: " + similarity);
    }
    if (base.length != EMBEDDING_DIMENSION) {
      throw new IllegalArgumentException(
          "Base vector must have dimension " + EMBEDDING_DIMENSION + ", got: " + base.length);
    }

    float[] normalizedBase = normalize(base);

    // Cas trivial: similarité ≈ 1 → retourner une copie du vecteur de base
    if (similarity >= SIMILARITY_IDENTITY_THRESHOLD) {
      return normalizedBase.clone();
    }

    // Générer un vecteur aléatoire déterministe (seed dérivée du vecteur base)
    // et le rendre orthogonal à base via Gram-Schmidt
    long seed = Arrays.hashCode(base);
    float[] random = generate(seed);
    float[] orthogonal = gramSchmidt(random, normalizedBase);

    // Construire le vecteur résultat: s * base + sqrt(1-s²) * orthogonal
    double sqrtComplement = Math.sqrt(1 - similarity * similarity);
    float[] result = new float[EMBEDDING_DIMENSION];

    for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
      result[i] = (float) (similarity * normalizedBase[i] + sqrtComplement * orthogonal[i]);
    }

    return normalize(result);
  }

  /**
   * Normalise un vecteur (norme L2 = 1).
   *
   * @param vector vecteur à normaliser
   * @return nouveau vecteur normalisé
   */
  public static float[] normalize(float[] vector) {
    if (vector == null) {
      throw new IllegalArgumentException("Vector cannot be null");
    }

    double norm = 0;
    for (float v : vector) {
      norm += v * v;
    }
    norm = Math.sqrt(norm);

    if (norm < 1e-10) {
      throw new IllegalArgumentException("Cannot normalize zero vector");
    }

    float[] normalized = new float[vector.length];
    for (int i = 0; i < vector.length; i++) {
      normalized[i] = (float) (vector[i] / norm);
    }
    return normalized;
  }

  /**
   * Calcule la similarité cosinus entre deux vecteurs.
   *
   * @param a premier vecteur
   * @param b second vecteur
   * @return similarité cosinus [-1, 1]
   */
  public static double cosineSimilarity(float[] a, float[] b) {
    if (a == null || b == null) {
      throw new IllegalArgumentException("Vectors cannot be null");
    }
    if (a.length != b.length) {
      throw new IllegalArgumentException("Vectors must have same dimension");
    }

    double dotProduct = 0;
    double normA = 0;
    double normB = 0;

    for (int i = 0; i < a.length; i++) {
      dotProduct += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }

    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  /**
   * Orthogonalise un vecteur par rapport à un vecteur de référence via Gram-Schmidt.
   *
   * @param vector vecteur à orthogonaliser
   * @param reference vecteur de référence (doit être normalisé)
   * @return vecteur orthogonal normalisé
   */
  private static float[] gramSchmidt(float[] vector, float[] reference) {
    // Projection de vector sur reference
    double projection = 0;
    for (int i = 0; i < vector.length; i++) {
      projection += vector[i] * reference[i];
    }

    // Soustraire la projection pour obtenir la composante orthogonale
    float[] orthogonal = new float[vector.length];
    for (int i = 0; i < vector.length; i++) {
      orthogonal[i] = (float) (vector[i] - projection * reference[i]);
    }

    return normalize(orthogonal);
  }
}
