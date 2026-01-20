# Phase 3: Graph Relations - Research

**Researched:** 2026-01-20
**Domain:** Apache AGE graph database, Cypher queries, document relationships
**Confidence:** HIGH

## Summary

Phase 3 implements graph relations in Apache AGE: parent-child relationships between chunks and cross-reference links between documents. The existing infrastructure (Phase 1) already has AGE configured with connection-init-sql loading `LOAD 'age'` and setting `search_path = ag_catalog` on every HikariCP connection. The graph `alexandria` was created during schema setup.

Apache AGE uses vertices and edges stored in the `alexandria` graph. We need two edge types: `HAS_CHILD` (Chunk -> Chunk) linking parent chunks to their children, and `REFERENCES` (Document -> Document) linking documents that reference each other via markdown links. Cypher queries are executed via the `cypher()` function with results typed as `agtype`.

Cross-reference detection uses CommonMark's visitor pattern to extract Link nodes from parsed markdown. Links are resolved from relative paths to document IDs using the existing `DocumentRepository`.

**Primary recommendation:** Use standard JDBC with raw SQL wrapping Cypher queries; do not use the AGE JDBC driver (adds dependency, minimal benefit for our use case). Parse agtype results as JSON strings using Gson.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| PostgreSQL JDBC | 42.7.x | Database connectivity | Already in pom.xml, HikariCP managed |
| Apache AGE | 1.6.0 | Graph extension | Already installed in Docker, connection-init configured |
| commonmark | 0.22.0 | Markdown parsing | Already in pom.xml from Phase 2 |
| Gson | 2.10.x | agtype JSON parsing | Spring Boot managed, lightweight |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring JdbcTemplate | 3.4.1 | Query execution | Already used in infra layer |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Gson for agtype | AGE JDBC driver (Agtype class) | Extra dependency; Gson sufficient for our simple use |
| CommonMark visitor | Regex for markdown links | Regex misses edge cases (escapes, code blocks) |

**No additional dependencies needed.** All required libraries are already in pom.xml.

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/fr/kalifazzia/alexandria/
├── core/
│   ├── ingestion/
│   │   ├── CrossReferenceExtractor.java    # Extract links from markdown
│   │   └── IngestionService.java           # Extended to call graph service
│   └── port/
│       └── GraphRepository.java            # Port interface for graph operations
├── infra/
│   └── persistence/
│       └── AgeGraphRepository.java         # AGE implementation via JDBC
```

### Pattern 1: Cypher Query Execution via JDBC

**What:** Execute Cypher queries using standard JDBC, wrapping queries in the `cypher()` function.
**When to use:** All graph operations.

```java
// Source: Apache AGE documentation + project pattern
public class AgeGraphRepository implements GraphRepository {

    private final JdbcTemplate jdbcTemplate;
    private static final String GRAPH_NAME = "alexandria";

    /**
     * Executes a Cypher query and returns results as List of Maps.
     * Each map contains the columns specified in the AS clause.
     */
    public List<Map<String, Object>> executeCypher(String cypherQuery, String... returnColumns) {
        // Build column definition: col1 agtype, col2 agtype, ...
        String columns = Arrays.stream(returnColumns)
            .map(col -> col + " agtype")
            .collect(Collectors.joining(", "));

        String sql = String.format(
            "SELECT * FROM cypher('%s', $$ %s $$) AS (%s)",
            GRAPH_NAME, cypherQuery, columns
        );

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < returnColumns.length; i++) {
                String agtypeStr = rs.getString(i + 1);
                row.put(returnColumns[i], parseAgtype(agtypeStr));
            }
            return row;
        });
    }

    /**
     * Parse agtype string to Java object.
     * Format: {"id": 123, "label": "Chunk", "properties": {...}}::vertex
     */
    private Object parseAgtype(String agtypeStr) {
        if (agtypeStr == null) return null;

        // Strip type suffix (::vertex, ::edge)
        String jsonStr = agtypeStr
            .replaceAll("::vertex$", "")
            .replaceAll("::edge$", "");

        try {
            return new Gson().fromJson(jsonStr, Map.class);
        } catch (Exception e) {
            // Return raw value for primitives (strings, numbers)
            return agtypeStr;
        }
    }
}
```

### Pattern 2: Creating Vertices for Chunks and Documents

**What:** Create vertices in the graph when chunks/documents are saved.
**When to use:** In IngestionService after saving to PostgreSQL tables.

```java
// Source: Apache AGE CREATE clause documentation
public void createChunkVertex(UUID chunkId, ChunkType type, UUID documentId) {
    String cypher = String.format("""
        CREATE (:Chunk {
            chunk_id: '%s',
            type: '%s',
            document_id: '%s'
        })
        """, chunkId, type.name().toLowerCase(), documentId);

    // Execute without return columns (terminal clause)
    jdbcTemplate.execute(String.format(
        "SELECT * FROM cypher('%s', $$ %s $$) AS (v agtype)",
        GRAPH_NAME, cypher
    ));
}

public void createDocumentVertex(UUID documentId, String path) {
    String cypher = String.format("""
        CREATE (:Document {
            document_id: '%s',
            path: '%s'
        })
        """, documentId, escapeCypher(path));

    jdbcTemplate.execute(String.format(
        "SELECT * FROM cypher('%s', $$ %s $$) AS (v agtype)",
        GRAPH_NAME, cypher
    ));
}
```

### Pattern 3: Creating Edges for Parent-Child Relationships

**What:** Create HAS_CHILD edges linking parent chunks to child chunks.
**When to use:** After saving child chunks in IngestionService.

```java
// Source: Apache AGE MATCH + CREATE pattern
public void createParentChildEdge(UUID parentChunkId, UUID childChunkId) {
    String cypher = String.format("""
        MATCH (p:Chunk {chunk_id: '%s'}), (c:Chunk {chunk_id: '%s'})
        CREATE (p)-[:HAS_CHILD]->(c)
        """, parentChunkId, childChunkId);

    jdbcTemplate.execute(String.format(
        "SELECT * FROM cypher('%s', $$ %s $$) AS (e agtype)",
        GRAPH_NAME, cypher
    ));
}
```

### Pattern 4: Creating Edges for Cross-References

**What:** Create REFERENCES edges linking documents that reference each other.
**When to use:** After extracting links from markdown content.

```java
// Source: Apache AGE MATCH + CREATE pattern
public void createReferenceEdge(UUID sourceDocId, UUID targetDocId, String linkText) {
    String cypher = String.format("""
        MATCH (s:Document {document_id: '%s'}), (t:Document {document_id: '%s'})
        CREATE (s)-[:REFERENCES {link_text: '%s'}]->(t)
        """, sourceDocId, targetDocId, escapeCypher(linkText));

    jdbcTemplate.execute(String.format(
        "SELECT * FROM cypher('%s', $$ %s $$) AS (e agtype)",
        GRAPH_NAME, cypher
    ));
}
```

### Pattern 5: Extracting Links from Markdown with CommonMark

**What:** Use CommonMark visitor to extract Link nodes from parsed markdown.
**When to use:** During ingestion to detect cross-references.

```java
// Source: CommonMark visitor pattern documentation
public class CrossReferenceExtractor extends AbstractVisitor {

    private final List<ExtractedLink> links = new ArrayList<>();

    @Override
    public void visit(Link link) {
        String destination = link.getDestination();

        // Only process internal markdown links
        if (destination != null && !destination.isEmpty()
                && !destination.startsWith("http://")
                && !destination.startsWith("https://")
                && destination.endsWith(".md")) {

            // Extract link text from children
            StringBuilder textBuilder = new StringBuilder();
            Node child = link.getFirstChild();
            while (child != null) {
                if (child instanceof Text) {
                    textBuilder.append(((Text) child).getLiteral());
                }
                child = child.getNext();
            }

            links.add(new ExtractedLink(destination, textBuilder.toString()));
        }

        visitChildren(link);
    }

    public List<ExtractedLink> getLinks() {
        return List.copyOf(links);
    }
}

record ExtractedLink(String relativePath, String linkText) {}
```

### Pattern 6: Traversal Queries for Phase 5

**What:** Cypher queries to find related documents via graph traversal.
**When to use:** In Phase 5 search implementation.

```java
// Find documents referenced by a document (1 hop)
public List<UUID> findReferencedDocuments(UUID documentId) {
    String cypher = String.format("""
        MATCH (d:Document {document_id: '%s'})-[:REFERENCES]->(ref:Document)
        RETURN ref.document_id
        """, documentId);

    return executeCypher(cypher, "ref_id").stream()
        .map(row -> UUID.fromString((String) row.get("ref_id")))
        .toList();
}

// Find documents within 2 hops (for related document discovery)
public List<UUID> findRelatedDocuments(UUID documentId, int maxHops) {
    String cypher = String.format("""
        MATCH (d:Document {document_id: '%s'})-[:REFERENCES*1..%d]->(related:Document)
        WHERE related.document_id <> '%s'
        RETURN DISTINCT related.document_id
        """, documentId, maxHops, documentId);

    return executeCypher(cypher, "related_id").stream()
        .map(row -> UUID.fromString((String) row.get("related_id")))
        .toList();
}

// Find parent chunk for a child
public Optional<UUID> findParentChunk(UUID childChunkId) {
    String cypher = String.format("""
        MATCH (p:Chunk)-[:HAS_CHILD]->(c:Chunk {chunk_id: '%s'})
        RETURN p.chunk_id
        """, childChunkId);

    List<Map<String, Object>> results = executeCypher(cypher, "parent_id");
    return results.isEmpty()
        ? Optional.empty()
        : Optional.of(UUID.fromString((String) results.get(0).get("parent_id")));
}
```

### Anti-Patterns to Avoid

- **Using AGE JDBC driver:** Adds external dependency when standard JDBC + Gson works fine
- **Storing full content in graph:** Store only IDs as properties; content is in PostgreSQL tables
- **Creating edges without vertices:** Always create vertices first, then edges
- **Variable-length paths with CREATE/MERGE:** AGE does not support `CREATE (a)-[:REL*]->(b)`
- **Forgetting AS clause:** Every `cypher()` call needs `AS (col1 agtype, ...)`
- **Not escaping strings in Cypher:** Apostrophes and backslashes need escaping

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Markdown link extraction | Regex pattern | CommonMark visitor | Edge cases: escaped brackets, links in code blocks |
| agtype parsing | Custom parser | Gson + String.replace | agtype is JSON with suffix |
| Path resolution | String manipulation | Path.resolve() | Handles ../relative correctly |
| Graph ID generation | Custom IDs | AGE auto-generates | AGE uses graphid internally |

**Key insight:** AGE handles vertex/edge IDs internally. Store our UUID (chunk_id, document_id) as properties to link back to PostgreSQL tables.

## Common Pitfalls

### Pitfall 1: Forgetting Connection Initialization
**What goes wrong:** `function cypher(unknown, unknown) does not exist`
**Why it happens:** AGE not loaded or search_path not set
**How to avoid:** Already handled by HikariCP `connection-init-sql` in application.yml
**Warning signs:** Any "cypher function not found" error

### Pitfall 2: Missing AS Clause
**What goes wrong:** SQL syntax error
**Why it happens:** Cypher function returns SETOF records requiring type definition
**How to avoid:** Always specify `AS (col1 agtype, col2 agtype, ...)` even for CREATE
**Warning signs:** "RETURN argument is not supported"

### Pitfall 3: String Injection in Cypher
**What goes wrong:** Cypher injection, syntax errors with apostrophes
**Why it happens:** Unescaped strings in Cypher queries
**How to avoid:** Escape single quotes (`'` -> `''`) and backslashes in string values
**Warning signs:** Syntax errors when file paths contain special characters

```java
private String escapeCypher(String value) {
    if (value == null) return null;
    return value.replace("\\", "\\\\").replace("'", "''");
}
```

### Pitfall 4: Creating Edges Before Vertices
**What goes wrong:** Empty result or silent failure
**Why it happens:** MATCH finds no vertices, CREATE never executes
**How to avoid:** Create Document/Chunk vertices during save, then edges
**Warning signs:** Edges missing after ingestion

### Pitfall 5: Orphan Vertices After Re-indexing
**What goes wrong:** Old vertices remain in graph after document re-indexed
**Why it happens:** Delete from PostgreSQL but not from AGE graph
**How to avoid:** Delete vertices (cascade deletes edges) when deleting document
**Warning signs:** Duplicate vertices with same document_id

### Pitfall 6: Relative Path Resolution
**What goes wrong:** Cross-references not found
**Why it happens:** `../other.md` not resolved correctly from source file's directory
**How to avoid:** Use `sourceFile.getParent().resolve(relativePath).normalize()`
**Warning signs:** Valid links in markdown not creating REFERENCES edges

## Code Examples

### Complete Graph Repository Implementation

```java
// Source: Apache AGE documentation + project patterns
@Repository
public class AgeGraphRepository implements GraphRepository {

    private static final String GRAPH_NAME = "alexandria";
    private final JdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();

    public AgeGraphRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createDocumentVertex(UUID documentId, String path) {
        executeCypherUpdate("""
            CREATE (:Document {document_id: '%s', path: '%s'})
            """, documentId, escapeCypher(path));
    }

    @Override
    public void createChunkVertex(UUID chunkId, ChunkType type, UUID documentId) {
        executeCypherUpdate("""
            CREATE (:Chunk {chunk_id: '%s', type: '%s', document_id: '%s'})
            """, chunkId, type.name().toLowerCase(), documentId);
    }

    @Override
    public void createParentChildEdge(UUID parentId, UUID childId) {
        executeCypherUpdate("""
            MATCH (p:Chunk {chunk_id: '%s'}), (c:Chunk {chunk_id: '%s'})
            CREATE (p)-[:HAS_CHILD]->(c)
            """, parentId, childId);
    }

    @Override
    public void createReferenceEdge(UUID sourceDocId, UUID targetDocId, String linkText) {
        executeCypherUpdate("""
            MATCH (s:Document {document_id: '%s'}), (t:Document {document_id: '%s'})
            CREATE (s)-[:REFERENCES {link_text: '%s'}]->(t)
            """, sourceDocId, targetDocId, escapeCypher(linkText));
    }

    @Override
    public void deleteDocumentVertex(UUID documentId) {
        // DETACH DELETE removes vertex and all connected edges
        executeCypherUpdate("""
            MATCH (d:Document {document_id: '%s'})
            DETACH DELETE d
            """, documentId);
    }

    @Override
    public void deleteChunksByDocumentId(UUID documentId) {
        executeCypherUpdate("""
            MATCH (c:Chunk {document_id: '%s'})
            DETACH DELETE c
            """, documentId);
    }

    @Override
    public List<UUID> findRelatedDocuments(UUID documentId, int maxHops) {
        String cypher = String.format("""
            MATCH (d:Document {document_id: '%s'})-[:REFERENCES*1..%d]->(related:Document)
            WHERE related.document_id <> '%s'
            RETURN DISTINCT related.document_id AS doc_id
            """, documentId, maxHops, documentId);

        return executeCypherQuery(cypher, "doc_id").stream()
            .map(row -> {
                Object docId = row.get("doc_id");
                // Handle quoted string from agtype
                String idStr = docId.toString().replace("\"", "");
                return UUID.fromString(idStr);
            })
            .toList();
    }

    private void executeCypherUpdate(String cypherTemplate, Object... args) {
        String cypher = String.format(cypherTemplate, args);
        String sql = String.format(
            "SELECT * FROM cypher('%s', $$ %s $$) AS (result agtype)",
            GRAPH_NAME, cypher
        );
        jdbcTemplate.execute(sql);
    }

    private List<Map<String, Object>> executeCypherQuery(String cypher, String... columns) {
        String columnDef = Arrays.stream(columns)
            .map(c -> c + " agtype")
            .collect(Collectors.joining(", "));

        String sql = String.format(
            "SELECT * FROM cypher('%s', $$ %s $$) AS (%s)",
            GRAPH_NAME, cypher, columnDef
        );

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < columns.length; i++) {
                row.put(columns[i], parseAgtype(rs.getString(i + 1)));
            }
            return row;
        });
    }

    private Object parseAgtype(String agtypeStr) {
        if (agtypeStr == null) return null;
        String jsonStr = agtypeStr
            .replaceAll("::vertex$", "")
            .replaceAll("::edge$", "")
            .replaceAll("::path$", "");
        try {
            return gson.fromJson(jsonStr, Object.class);
        } catch (Exception e) {
            return agtypeStr;
        }
    }

    private String escapeCypher(String value) {
        if (value == null) return null;
        return value.replace("\\", "\\\\").replace("'", "''");
    }
}
```

### Complete Cross-Reference Extractor

```java
// Source: CommonMark documentation
public class CrossReferenceExtractor {

    private final Parser parser;

    public CrossReferenceExtractor() {
        // Reuse parser from MarkdownParser if possible
        this.parser = Parser.builder().build();
    }

    public List<ExtractedLink> extractLinks(String markdownContent) {
        Node document = parser.parse(markdownContent);
        LinkVisitor visitor = new LinkVisitor();
        document.accept(visitor);
        return visitor.getLinks();
    }

    /**
     * Resolves a relative link path to an absolute path.
     * @param sourceFilePath the path of the file containing the link
     * @param relativeLinkPath the relative path from the link
     * @return absolute path, or empty if outside base directory
     */
    public Optional<String> resolveLink(Path sourceFilePath, String relativeLinkPath) {
        try {
            Path sourceDir = sourceFilePath.getParent();
            if (sourceDir == null) return Optional.empty();

            Path resolved = sourceDir.resolve(relativeLinkPath).normalize();
            return Optional.of(resolved.toAbsolutePath().toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static class LinkVisitor extends AbstractVisitor {
        private final List<ExtractedLink> links = new ArrayList<>();

        @Override
        public void visit(Link link) {
            String dest = link.getDestination();
            if (isInternalMarkdownLink(dest)) {
                links.add(new ExtractedLink(dest, extractLinkText(link)));
            }
            visitChildren(link);
        }

        private boolean isInternalMarkdownLink(String destination) {
            if (destination == null || destination.isEmpty()) return false;
            if (destination.startsWith("http://")) return false;
            if (destination.startsWith("https://")) return false;
            if (destination.startsWith("mailto:")) return false;
            if (destination.startsWith("#")) return false; // anchor
            return destination.endsWith(".md");
        }

        private String extractLinkText(Link link) {
            StringBuilder sb = new StringBuilder();
            Node child = link.getFirstChild();
            while (child != null) {
                if (child instanceof Text text) {
                    sb.append(text.getLiteral());
                }
                child = child.getNext();
            }
            return sb.toString();
        }

        public List<ExtractedLink> getLinks() {
            return List.copyOf(links);
        }
    }

    public record ExtractedLink(String relativePath, String linkText) {}
}
```

### Integration in IngestionService

```java
// Extended IngestionService.ingestFile() method
@Transactional
public void ingestFile(Path file) {
    // ... existing code to parse and save document ...

    Document document = documentRepository.save(/* ... */);

    // Create document vertex in graph
    graphRepository.createDocumentVertex(document.id(), document.path());

    // ... existing chunking code ...

    for (ChunkPair pair : chunkPairs) {
        UUID parentId = chunkRepository.saveChunk(/* parent */);

        // Create parent chunk vertex
        graphRepository.createChunkVertex(parentId, ChunkType.PARENT, document.id());

        for (int i = 0; i < pair.childContents().size(); i++) {
            UUID childId = chunkRepository.saveChunk(/* child */);

            // Create child chunk vertex
            graphRepository.createChunkVertex(childId, ChunkType.CHILD, document.id());

            // Create parent-child edge
            graphRepository.createParentChildEdge(parentId, childId);
        }
    }

    // Extract and store cross-references
    List<ExtractedLink> links = crossReferenceExtractor.extractLinks(parsed.content());
    for (ExtractedLink link : links) {
        Optional<String> resolvedPath = crossReferenceExtractor
            .resolveLink(file, link.relativePath());

        if (resolvedPath.isPresent()) {
            Optional<Document> targetDoc = documentRepository
                .findByPath(resolvedPath.get());

            targetDoc.ifPresent(target ->
                graphRepository.createReferenceEdge(
                    document.id(), target.id(), link.linkText()
                )
            );
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Neo4j separate database | Apache AGE in PostgreSQL | AGE 1.0+ | Single database, ACID with relations |
| Custom graph schema | AGE managed vertices/edges | AGE | No custom tables for graph |
| String concatenation for Cypher | Parameterized queries | Security best practice | Prevents injection |

**Deprecated/outdated:**
- Neo4j Java driver for simple relationships (overkill when AGE available)
- Custom adjacency tables for graph (AGE handles this)

## Open Questions

1. **Order of cross-reference creation**
   - What we know: Source document must exist before creating REFERENCES edges
   - What's unclear: If target document not yet ingested, reference is lost
   - Recommendation: Phase 1 creates forward references only; add "deferred references" table in v2 if needed

2. **Performance with many edges**
   - What we know: AGE handles 83K edges in ~1hr for initial batch
   - What's unclear: Performance impact of per-chunk vertex creation during ingestion
   - Recommendation: Batch Cypher queries if ingestion becomes slow; monitor in integration tests

3. **Index on vertex properties**
   - What we know: AGE supports GIN indexes on properties
   - What's unclear: Whether index on `chunk_id`/`document_id` properties improves MATCH performance
   - Recommendation: Start without index; add if MATCH queries slow with large graphs

## Sources

### Primary (HIGH confidence)
- [Apache AGE CREATE Documentation](https://age.apache.org/age-manual/v0.6.0/clauses/create.html) - Vertex and edge creation syntax
- [Apache AGE MATCH Documentation](https://age.apache.org/age-manual/master/clauses/match.html) - Query patterns, variable-length paths
- [Apache AGE MERGE Documentation](https://age.apache.org/age-manual/master/clauses/merge.html) - Upsert pattern
- [Apache AGE Cypher Query Format](https://age.apache.org/age-manual/master/intro/cypher.html) - cypher() function syntax
- [Apache AGE Data Types](https://age.apache.org/age-manual/master/intro/types.html) - agtype format for vertices/edges
- [CommonMark Java GitHub](https://github.com/commonmark/commonmark-java) - Visitor pattern, Link node API
- [AGE JDBC Driver](https://pgxn.org/dist/apacheage/drivers/jdbc/README.html) - Agtype parsing reference

### Secondary (MEDIUM confidence)
- [Handling Hierarchical Data in Apache AGE](https://dev.to/humzakt/handling-hierarchical-data-in-apache-age-a-comprehensive-guide-22l2) - Parent-child patterns
- [Graph Data Modeling Best Practices](https://dev.to/abdulsamad4068/graph-data-modeling-best-practices-a-comprehensive-guide-for-apache-agedb-7hm) - Naming conventions, design patterns
- [Interacting with Apache AGE Best Practices](https://dev.to/humzakt/interacting-with-apache-age-best-practices-for-application-developers-hgh) - JDBC integration

### Tertiary (LOW confidence)
- [age-utils Spring/MyBatis](https://github.com/fabiomarini/age-utils) - Alternative approach (not using)

## Metadata

**Confidence breakdown:**
- Cypher query syntax: HIGH - Official AGE documentation verified
- JDBC integration pattern: HIGH - Based on existing project patterns + verified AGE docs
- CommonMark link extraction: HIGH - Official API documented, used in Phase 2
- Performance characteristics: MEDIUM - Based on GitHub issues, not benchmarked
- Deferred references: LOW - Design decision, not researched in depth

**Research date:** 2026-01-20
**Valid until:** 2026-02-20 (AGE 1.6.0 stable; CommonMark 0.22.0 stable)

---
*Research completed: 2026-01-20*
*Sources: Apache AGE official documentation, CommonMark GitHub, AGE community articles*
