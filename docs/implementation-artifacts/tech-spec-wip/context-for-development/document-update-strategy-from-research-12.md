# Document Update Strategy (from research #12)

**Principe:** Identification hybride (chemin + hash SHA-256) avec pattern DELETE + INSERT transactionnel.

**Stratégie d'identification:**

| Stratégie | Déduplication | Détection changements | Traçabilité | Renommage |
|-----------|--------------|----------------------|-------------|-----------|
| Chemin seul | ❌ Faible | ❌ Non | ✅ Excellente | ❌ Doublons |
| Hash seul | ✅ Parfaite | ✅ Automatique | ❌ Perdue | ✅ OK |
| **Hybride** | ✅ Parfaite | ✅ Automatique | ✅ Complète | ✅ OK |

**Choix Alexandria:** Hybride avec `sourceUri` (chemin logique) + `documentHash` (SHA-256).

**Pattern DELETE + INSERT transactionnel:**

```java
package dev.alexandria.core;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Service de mise à jour des documents avec détection de changements.
 * Pattern: DELETE anciens chunks → INSERT nouveaux (atomique par document).
 */
@Service
public class DocumentUpdateService {

    private final PgVectorEmbeddingStore embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final AlexandriaMarkdownSplitter splitter;

    /**
     * Algorithme de détection de changement en deux phases.
     * Phase 1: Fast path (mtime + size) - évite recalcul hash si inchangé.
     * Phase 2: Hash SHA-256 pour confirmation.
     */
    public UpdateResult processDocument(Path filePath, Path basePath) throws IOException {
        String sourceUri = ChunkMetadata.toLogicalUri(filePath, basePath);
        String content = Files.readString(filePath);
        String documentHash = ChunkMetadata.computeHash(content);

        // Phase 1: Vérification rapide via métadonnées fichier
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        Optional<StoredDocumentInfo> stored = getStoredDocumentInfo(sourceUri);

        if (stored.isPresent()) {
            StoredDocumentInfo info = stored.get();
            // Fast path: si taille et date identiques, probablement inchangé
            if (info.fileSize() == attrs.size()
                && info.fileModifiedAt().equals(attrs.lastModifiedTime().toInstant())) {
                // Phase 2: Validation par hash
                if (documentHash.equals(info.documentHash())) {
                    return UpdateResult.NO_CHANGE;
                }
            }
        }

        // Document nouveau ou modifié : procéder à l'ingestion
        boolean isNewDocument = stored.isEmpty();
        return ingestDocument(sourceUri, content, documentHash, attrs, isNewDocument);
    }

    private UpdateResult ingestDocument(String sourceUri, String content,
                                        String documentHash, BasicFileAttributes attrs,
                                        boolean isNewDocument) {
        // 1. Supprimer tous les anciens chunks du document
        Filter documentFilter = metadataKey("sourceUri").isEqualTo(sourceUri);
        embeddingStore.removeAll(documentFilter);

        // 2. Découper le document en chunks (splitter enrichit avec breadcrumbs)
        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);

        // 3. Extraire le titre du document (premier H1 ou filename)
        String documentTitle = extractDocumentTitle(content, sourceUri);

        // 4. Préparer les segments avec métadonnées enrichies
        List<TextSegment> enrichedSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);

            // breadcrumbs déjà présent si splitter markdown-aware
            String breadcrumbs = segment.metadata().getString("breadcrumbs");
            if (breadcrumbs == null) {
                breadcrumbs = documentTitle;  // Fallback si pas de headers
            }

            Metadata metadata = new Metadata()
                .put("sourceUri", sourceUri)
                .put("documentHash", documentHash)
                .put("chunkIndex", i)
                .put("breadcrumbs", breadcrumbs)
                .put("documentTitle", documentTitle)
                .put("contentHash", ChunkMetadata.computeHash(segment.text()))
                .put("createdAt", Instant.now().toEpochMilli())
                .put("documentType", detectDocType(sourceUri));

            enrichedSegments.add(TextSegment.from(segment.text(), metadata));
        }

        // 5. Générer embeddings et insérer
        // Note: embedAll() prend List<TextSegment>, pas List<String>
        Response<List<Embedding>> response = embeddingModel.embedAll(enrichedSegments);
        List<Embedding> embeddings = response.content();

        embeddingStore.addAll(embeddings, enrichedSegments);

        return isNewDocument ? UpdateResult.CREATED : UpdateResult.UPDATED;
    }

    /**
     * Extrait le titre du document depuis le premier H1 ou le nom de fichier.
     */
    private String extractDocumentTitle(String content, String sourceUri) {
        // Chercher le premier header H1
        Pattern h1Pattern = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = h1Pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // Fallback: nom du fichier sans extension
        return sourceUri.substring(sourceUri.lastIndexOf('/') + 1)
                       .replaceFirst("\\.[^.]+$", "");
    }

    /**
     * Suppression explicite d'un document (hard delete).
     */
    public void deleteDocument(String sourceUri) {
        Filter filter = metadataKey("sourceUri").isEqualTo(sourceUri);
        embeddingStore.removeAll(filter);
    }

    public enum UpdateResult { NO_CHANGE, CREATED, UPDATED }

    private record StoredDocumentInfo(String documentHash, long fileSize, Instant fileModifiedAt) {}
}
```

**Scénarios gérés:**

| Scénario | Détection | Action |
|----------|-----------|--------|
| Fichier modifié | Hash différent | DELETE + INSERT |
| Fichier renommé | Même hash, URI différent | Détectable (future v2) |
| Fichier supprimé | URI absent du filesystem | `deleteDocument()` |
| Ré-ingestion accidentelle | Même hash | `NO_CHANGE` (skip) |

**Reporté à v2:**
- Soft delete avec colonne `is_active`
- Garbage collection automatique des chunks orphelins
- Détection de renommage par hash (même contenu, nouveau chemin)
