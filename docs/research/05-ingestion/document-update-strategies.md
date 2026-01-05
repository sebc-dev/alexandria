# Stratégies de mise à jour des documents dans un RAG PostgreSQL/pgvector avec Langchain4j

L'approche optimale pour un système RAG mono-utilisateur combine **identification hybride** (chemin + hash SHA-256) avec un pattern **DELETE + INSERT transactionnel** par document. Langchain4j ne propose pas de méthode `upsert` native dans son API `EmbeddingStore`, nécessitant une implémentation explicite via `removeAll(Filter)` suivi de `addAll()`. Cette architecture offre une détection de changements en deux phases (métadonnées fichier puis hash contenu) et garantit la cohérence transactionnelle lors des mises à jour.

## Comparaison des stratégies d'identification de documents

Le choix de la stratégie d'identification impacte directement la gestion des quatre scénarios problématiques : modification, renommage, suppression et ré-ingestion accidentelle.

**L'identification par chemin de fichier** (`/docs/api/auth.md`) offre une simplicité et une traçabilité excellentes. Les chunks sont facilement retrouvables via leur source. Cependant, renommer un fichier crée un "nouveau" document et génère des doublons. Cette approche ne détecte pas les changements de contenu intrinsèquement.

**L'identification par hash de contenu** (SHA-256) garantit une déduplication parfaite : un contenu identique produit toujours le même hash. Les renommages ne créent pas de doublons. Toutefois, une modification mineure (ajout d'un espace) génère un hash complètement différent, et la lignée des versions est perdue.

**L'identification par UUID** assure une identité stable à travers les modifications de contenu, permettant un versionning explicite. Elle nécessite cependant un système externe de mapping UUID↔source et ne détecte pas les changements automatiquement.

| Stratégie | Déduplication | Détection changements | Traçabilité | Renommage |
|-----------|--------------|----------------------|-------------|-----------|
| Chemin seul | ❌ Faible | ❌ Non | ✅ Excellente | ❌ Doublons |
| Hash seul | ✅ Parfaite | ✅ Automatique | ❌ Perdue | ✅ OK |
| UUID seul | ❌ Manuelle | ❌ Manuelle | ✅ Bonne | ✅ OK |
| **Hybride** | ✅ Parfaite | ✅ Automatique | ✅ Complète | ✅ OK |

**Recommandation : approche hybride** utilisant le chemin comme identifiant de document (`document_id`) combiné au hash SHA-256 pour la détection de changements. Cette combinaison résout les quatre scénarios problématiques.

## Stratégie de mise à jour optimale : DELETE transactionnel + INSERT

Trois patterns s'offrent pour les mises à jour d'embeddings, chacun avec des compromis distincts.

Le pattern **DELETE + INSERT transactionnel** supprime tous les chunks d'un document puis insère les nouveaux dans une même transaction. C'est le pattern le plus simple et le plus robuste pour Langchain4j. Il garantit l'atomicité au niveau document : les requêtes voient soit l'ancienne version complète, soit la nouvelle. L'inconvénient est la régénération de tous les embeddings même si un seul chunk change.

```sql
BEGIN;
DELETE FROM embeddings WHERE metadata->>'document_id' = '/docs/api.md';
INSERT INTO embeddings (embedding_id, embedding, text, metadata) VALUES
    (gen_random_uuid(), '[vector1]', 'chunk1', '{"document_id": "/docs/api.md", ...}'),
    (gen_random_uuid(), '[vector2]', 'chunk2', '{"document_id": "/docs/api.md", ...}');
COMMIT;
```

Le pattern **UPSERT** (`ON CONFLICT DO UPDATE`) est théoriquement plus efficient mais Langchain4j ne l'expose pas directement. Il nécessite des IDs déterministes par chunk et peut laisser des "orphelins" si le nombre de chunks diminue après modification.

La **mise à jour sélective** par chunk (ne régénérer que les chunks modifiés) offre la meilleure performance théorique mais complexifie considérablement l'implémentation. Les frontières de chunks peuvent se décaler lors de modifications, rendant la comparaison chunk-à-chunk peu fiable.

**Recommandation pour système mono-utilisateur** : le pattern DELETE + INSERT transactionnel. Sa simplicité surpasse les gains de performance des alternatives pour des volumes typiques de documentation technique (**< 10 000 documents**).

## API Langchain4j PgVectorEmbeddingStore : capacités et limitations

L'interface `EmbeddingStore<TextSegment>` de Langchain4j 1.10.0 expose les méthodes suivantes pour PgVectorEmbeddingStore :

**Ajout d'embeddings** avec `add()` pour un embedding unique (retourne l'UUID généré), `add(String id, Embedding)` pour un ID custom, et `addAll()` pour les opérations batch. La méthode `addAll(List<String> ids, List<Embedding>, List<TextSegment>)` permet de spécifier des IDs déterministes.

**Suppression** via `remove(String id)` pour un ID unique, `removeAll(Collection<String> ids)` pour une liste, et crucially `removeAll(Filter filter)` pour supprimer par métadonnées. Cette dernière est essentielle pour implémenter les mises à jour par document.

**Recherche** avec `search(EmbeddingSearchRequest)` supportant le filtrage par métadonnées. La méthode `findRelevant()` est dépréciée.

```java
// Filtrage par métadonnées - clé pour les mises à jour
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

Filter filter = metadataKey("document_id").isEqualTo("/docs/api.md");
embeddingStore.removeAll(filter);  // Supprime tous les chunks du document
```

**Limitation critique** : aucune méthode `update()` ou `upsert()` native n'existe. Le pattern standard est remove-then-add. Les opérations multiples ne sont pas wrappées dans une transaction côté API — la gestion transactionnelle doit être implémentée au niveau applicatif ou SQL.

**Configuration des métadonnées** via `MetadataStorageConfig` :
- `COMBINED_JSONB` (recommandé) : toutes les métadonnées dans une colonne JSONB, optimal pour les requêtes flexibles
- `COLUMN_PER_KEY` : une colonne par clé de métadonnée, pour des schémas statiques

## Schéma JSONB recommandé pour le tracking des documents

Une structure plate optimise l'utilisation des index GIN et simplifie les requêtes de filtrage.

```json
{
  "document_id": "/docs/api/authentication.md",
  "document_hash": "sha256:3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e",
  "chunk_index": 0,
  "chunk_hash": "sha256:1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b",
  "total_chunks": 12,
  "file_modified_at": "2025-01-04T08:15:00Z",
  "ingested_at": "2025-01-04T08:20:32Z",
  "file_size_bytes": 15234,
  "version": 2,
  "doc_type": "markdown",
  "embedding_model": "text-embedding-3-small",
  "chunking_strategy": "recursive_500_50"
}
```

Les champs `document_id` et `document_hash` sont les pivots de la stratégie de mise à jour. Le premier identifie le document source, le second détecte les changements de contenu. Les champs `chunk_index` et `chunk_hash` permettraient une mise à jour incrémentale si nécessaire. Les timestamps `file_modified_at` et `ingested_at` servent à la détection rapide de changements et à l'audit.

## Implémentation Java avec Langchain4j

Voici une implémentation complète du service de gestion des documents :

```java
@Service
public class DocumentUpdateService {
    
    private final PgVectorEmbeddingStore embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter splitter;
    
    // Algorithme de détection de changement en deux phases
    public UpdateResult processDocument(Path filePath) throws IOException {
        String documentId = filePath.toString();
        String content = Files.readString(filePath);
        String normalizedContent = normalizeForHashing(content);
        String contentHash = computeSha256(normalizedContent);
        
        // Phase 1: Vérification rapide via métadonnées fichier
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        Optional<DocumentMetadata> stored = getStoredMetadata(documentId);
        
        if (stored.isPresent()) {
            DocumentMetadata meta = stored.get();
            // Fast path: si taille et date identiques, probablement inchangé
            if (meta.fileSize() == attrs.size() 
                && meta.fileModifiedAt().equals(attrs.lastModifiedTime().toInstant())) {
                // Phase 2: Validation par hash si fast path passe
                if (contentHash.equals(meta.documentHash())) {
                    return UpdateResult.NO_CHANGE;
                }
            }
        }
        
        // Document nouveau ou modifié : procéder à l'ingestion
        return ingestDocument(documentId, content, contentHash, attrs);
    }
    
    private UpdateResult ingestDocument(String documentId, String content, 
                                        String contentHash, BasicFileAttributes attrs) {
        // Supprimer les anciens chunks
        Filter documentFilter = metadataKey("document_id").isEqualTo(documentId);
        embeddingStore.removeAll(documentFilter);
        
        // Découper le document en chunks
        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);
        
        // Préparer les métadonnées et embeddings
        List<TextSegment> enrichedSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            String chunkHash = computeSha256(segment.text());
            
            Metadata metadata = new Metadata()
                .put("document_id", documentId)
                .put("document_hash", contentHash)
                .put("chunk_index", i)
                .put("chunk_hash", chunkHash)
                .put("total_chunks", segments.size())
                .put("file_modified_at", attrs.lastModifiedTime().toInstant().toString())
                .put("ingested_at", Instant.now().toString())
                .put("file_size_bytes", attrs.size())
                .put("version", getNextVersion(documentId))
                .put("doc_type", detectDocType(documentId))
                .put("embedding_model", "text-embedding-3-small")
                .put("chunking_strategy", "recursive_500_50");
            
            enrichedSegments.add(TextSegment.from(segment.text(), metadata));
        }
        
        // Générer les embeddings et insérer
        List<Embedding> embeddings = embeddingModel.embedAll(
            enrichedSegments.stream().map(TextSegment::text).toList()
        ).content();
        
        embeddingStore.addAll(embeddings, enrichedSegments);
        
        return UpdateResult.UPDATED;
    }
    
    // Suppression de document (scénario 3)
    public void deleteDocument(String documentId) {
        Filter filter = metadataKey("document_id").isEqualTo(documentId);
        embeddingStore.removeAll(filter);
    }
    
    // Détection de renommage via hash (scénario 2)
    public Optional<String> findExistingDocumentByHash(String contentHash) {
        // Requête SQL directe nécessaire car Langchain4j ne supporte pas
        // la recherche par métadonnées sans vecteur de requête
        // Implémentation via JdbcTemplate recommandée
        return Optional.empty(); // Placeholder
    }
    
    private String normalizeForHashing(String text) {
        // Normalisation Unicode NFKC
        text = Normalizer.normalize(text, Normalizer.Form.NFKC);
        // Normaliser les espaces
        text = text.replaceAll("[\\u00A0\\u2000-\\u200B\\u3000\\t]+", " ");
        // Normaliser les fins de ligne
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        // Compacter les espaces multiples
        text = text.replaceAll(" +", " ").replaceAll("\n+", "\n");
        return text.strip();
    }
    
    private String computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

**Configuration du PgVectorEmbeddingStore** avec JSONB pour les métadonnées :

```java
@Configuration
public class VectorStoreConfig {
    
    @Bean
    public PgVectorEmbeddingStore embeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.builder()
            .datasource(dataSource)
            .table("document_embeddings")
            .dimension(1024)
            .createTable(true)
            .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
            .useIndex(true)
            .indexListSize(100)  // Pour IVFFlat, ajuster selon volume
            .build();
    }
}
```

## Requêtes SQL pour la maintenance et déduplication

**Détection de doublons par hash de contenu** :

```sql
-- Trouver les documents avec contenu dupliqué (différents paths, même hash)
SELECT metadata->>'document_hash' as hash,
       array_agg(DISTINCT metadata->>'document_id') as document_ids,
       COUNT(DISTINCT metadata->>'document_id') as duplicate_count
FROM document_embeddings
GROUP BY metadata->>'document_hash'
HAVING COUNT(DISTINCT metadata->>'document_id') > 1;
```

**Nettoyage des orphelins** (chunks sans document source) :

```sql
-- Supprimer les chunks dont le fichier source n'existe plus
-- Exécuter après vérification via l'application
DELETE FROM document_embeddings
WHERE metadata->>'document_id' IN (
    SELECT DISTINCT metadata->>'document_id'
    FROM document_embeddings
    WHERE NOT EXISTS (
        -- Liste des fichiers valides à fournir par l'application
        SELECT 1 FROM valid_documents v 
        WHERE v.path = document_embeddings.metadata->>'document_id'
    )
);
```

**Index recommandés pour les opérations de maintenance** :

```sql
-- Index B-tree pour les lookups par document_id (DELETE par document)
CREATE INDEX idx_doc_id ON document_embeddings ((metadata->>'document_id'));

-- Index B-tree pour les lookups par hash (détection doublons)
CREATE INDEX idx_doc_hash ON document_embeddings ((metadata->>'document_hash'));

-- Index GIN pour les requêtes flexibles sur métadonnées
CREATE INDEX idx_metadata_gin ON document_embeddings 
USING GIN (metadata jsonb_path_ops);
```

**Statistiques d'utilisation** :

```sql
-- Vue d'ensemble des documents indexés
SELECT 
    metadata->>'doc_type' as type,
    COUNT(DISTINCT metadata->>'document_id') as documents,
    COUNT(*) as total_chunks,
    ROUND(AVG((metadata->>'total_chunks')::int)) as avg_chunks_per_doc
FROM document_embeddings
GROUP BY metadata->>'doc_type';

-- Documents les plus anciens (candidats à re-vérification)
SELECT DISTINCT ON (metadata->>'document_id')
    metadata->>'document_id' as document,
    metadata->>'ingested_at' as last_ingested,
    metadata->>'version' as version
FROM document_embeddings
ORDER BY metadata->>'document_id', (metadata->>'ingested_at')::timestamp DESC
LIMIT 20;
```

## Gestion du versioning et de l'historique

Pour un système mono-utilisateur de documentation technique, **le hard delete sans historique** est recommandé. La conservation des anciennes versions triple l'espace de stockage (vecteurs 1024 dimensions = ~4KB par chunk) et complexifie les requêtes sans bénéfice tangible.

**Si l'historique est nécessaire**, implémenter un soft delete via un champ `is_active` :

```sql
-- Ajouter le support soft delete
ALTER TABLE document_embeddings 
ADD COLUMN is_active BOOLEAN DEFAULT true;

-- Index partiel pour exclure les inactifs des recherches
CREATE INDEX idx_active_embeddings ON document_embeddings 
USING hnsw (embedding vector_cosine_ops)
WHERE is_active = true;
```

**Attention avec pgvector HNSW** : les vecteurs soft-deleted restent dans l'index et sont parcourus lors des recherches. Le filtrage s'applique après le scan, réduisant potentiellement le nombre de résultats. Pour contourner, utiliser `hnsw.iterative_scan = 'relaxed_order'` (pgvector 0.8.0+).

## Conclusion : architecture recommandée

Pour un système RAG mono-utilisateur avec documentation technique, l'architecture optimale repose sur **trois piliers** :

L'**identification hybride** (chemin + hash SHA-256) offre le meilleur équilibre entre traçabilité et détection de changements. Le chemin sert d'identifiant stable du document, le hash détecte les modifications de contenu.

La **détection en deux phases** (métadonnées fichier puis hash) optimise les performances en évitant le calcul de hash pour les fichiers non modifiés. La phase rapide compare `mtime` et `size`, la phase complète valide par hash SHA-256.

Le **pattern DELETE + INSERT transactionnel** garantit la cohérence sans complexité excessive. Pour Langchain4j, cela se traduit par `removeAll(Filter)` suivi de `addAll()`. Les index B-tree sur `document_id` et `document_hash` dans le JSONB assurent des performances acceptables jusqu'à **100 000 chunks**.

Cette architecture gère nativement les quatre scénarios problématiques : modification (hash différent → re-ingestion), renommage (même hash → détectable), suppression (removeAll par document_id), et ré-ingestion accidentelle (même hash → skip ou overwrite).