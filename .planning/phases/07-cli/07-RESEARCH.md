# Phase 7: CLI - Research

**Researched:** 2026-01-20
**Domain:** Spring Boot CLI application, command-line interface for RAG system management
**Confidence:** HIGH

## Summary

This phase implements a command-line interface for Alexandria, providing commands to manage indexation (`index`, `status`, `clear`) and test search functionality (`search`). The application already has all core services implemented (IngestionService, SearchService, DocumentRepository, ChunkRepository, GraphRepository), so the CLI is primarily a new entry point into existing functionality.

Spring Shell 3.4.1 is the standard choice for Spring Boot CLI applications, providing deep Spring integration, annotation-based command definition, and both interactive and non-interactive execution modes. The new `@Command` annotation model (replacing legacy `@ShellMethod`) aligns with the project's modern Java 21 approach.

**Primary recommendation:** Use Spring Shell 3.4.1 with `@Command` annotation model, configure for non-interactive mode (execute command and exit), reuse existing services via constructor injection.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-shell-starter | 3.4.1 | CLI framework | Official Spring project, deep Spring Boot integration, maintained |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot | 3.4.1 | Application framework | Already in project |
| Bean Validation | 3.0 | Argument validation | Spring Shell integrates automatically |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring Shell | Picocli | Lighter weight, faster startup, but less Spring integration |
| Spring Shell | CommandLineRunner | Simple, no dependencies, but no argument parsing or help generation |

**Installation:**
```xml
<dependency>
    <groupId>org.springframework.shell</groupId>
    <artifactId>spring-shell-starter</artifactId>
    <version>3.4.1</version>
</dependency>
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/fr/kalifazzia/alexandria/
├── api/
│   ├── cli/                    # NEW: CLI commands
│   │   └── AlexandriaCommands.java
│   └── mcp/                    # Existing MCP tools
├── core/
│   ├── ingestion/              # Existing - reuse IngestionService
│   ├── search/                 # Existing - reuse SearchService
│   └── port/                   # May need new methods for status/clear
└── infra/
    └── persistence/            # May need new repository methods
```

### Pattern 1: Command Class with @Command Annotation
**What:** Single class annotated with `@Command` containing all CLI commands
**When to use:** When commands are related and share dependencies
**Example:**
```java
// Source: Spring Shell 3.4 documentation
@Command(group = "Alexandria Commands")
@Component
public class AlexandriaCommands {

    private final IngestionService ingestionService;
    private final SearchService searchService;
    private final DocumentRepository documentRepository;

    public AlexandriaCommands(
            IngestionService ingestionService,
            SearchService searchService,
            DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.searchService = searchService;
        this.documentRepository = documentRepository;
    }

    @Command(command = "index", description = "Index markdown files from a directory")
    public String index(@Option(longNames = "path", required = true) String path) {
        // Implementation
    }
}
```

### Pattern 2: Positional Arguments for Simple Commands
**What:** Use positional arguments to avoid `--option` syntax for simple commands
**When to use:** For commands with single required argument like `index <path>`
**Example:**
```java
// Source: Spring Shell positional arguments docs
@Command(command = "index", description = "Index markdown files from a directory")
public String index(
        @Option(longNames = "path", required = true, arity = OptionArity.EXACTLY_ONE)
        String path) {
    // With proper configuration, can be called as: index /path/to/docs
    // Or with explicit option: index --path /path/to/docs
}
```

### Pattern 3: Non-Interactive Mode Configuration
**What:** Configure Spring Shell to run single command and exit
**When to use:** For CLI tools that execute one command (vs interactive shell)
**Example:**
```yaml
# application.yml
spring:
  shell:
    interactive:
      enabled: false  # Disables interactive shell mode
```

### Anti-Patterns to Avoid
- **Mixing MCP and CLI profiles incorrectly:** The MCP server uses stdio transport - CLI commands must not interfere with MCP output. Use Spring profiles to separate concerns.
- **Not handling exit codes:** Non-interactive CLI commands should return proper exit codes for scripting (0 = success, non-zero = error).
- **Complex table formatting:** Spring Shell TableBuilder has ANSI color limitations. Keep output simple for shell compatibility.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Argument parsing | Manual String.split() | Spring Shell @Option | Handles types, validation, defaults, help text |
| Help generation | Manual --help handling | Spring Shell built-in | Automatic from annotations |
| Command registration | Manual command dispatch | Spring Shell @Command | Auto-discovery, grouping, aliases |
| Path validation | Manual exists/directory checks | Bean Validation + custom | Reusable, declarative |

**Key insight:** Spring Shell handles all CLI plumbing (argument parsing, help, tab completion, error messages). Focus on business logic in command methods.

## Common Pitfalls

### Pitfall 1: MCP and CLI Mode Conflict
**What goes wrong:** MCP server uses stdio transport; if CLI commands write to stdout, they corrupt MCP protocol
**Why it happens:** Both MCP and CLI are in same application, both want to use stdout
**How to avoid:** Use Spring profiles (`--spring.profiles.active=cli` vs `mcp`) to conditionally enable features
**Warning signs:** MCP client reports JSON parse errors; garbled output in Claude Code

### Pitfall 2: Missing Exit Codes in Non-Interactive Mode
**What goes wrong:** Script automation fails because CLI always returns 0 even on error
**Why it happens:** Spring Shell doesn't propagate exceptions to exit codes by default
**How to avoid:** Implement `CommandExceptionResolver` returning `CommandHandlingResult.of(message, exitCode)`
**Warning signs:** `echo $?` always shows 0 after failed commands

### Pitfall 3: Blocking Console During Long Operations
**What goes wrong:** `index` command with large directory shows no progress, appears frozen
**Why it happens:** Indexing can take minutes; no feedback to user
**How to avoid:** Use Spring Shell ProgressView or simple console output showing progress
**Warning signs:** Users kill process thinking it's hung

### Pitfall 4: Database Statistics Missing
**What goes wrong:** `status` command requires COUNT queries but no repository methods exist
**Why it happens:** Current repositories designed for CRUD, not statistics
**How to avoid:** Add new methods to DocumentRepository/ChunkRepository for counts and last-updated timestamps
**Warning signs:** Needing to write raw SQL in CLI layer

### Pitfall 5: Graph Data Orphaned After Clear
**What goes wrong:** `clear` command truncates PostgreSQL tables but leaves Apache AGE vertices
**Why it happens:** Graph data is separate from relational data
**How to avoid:** Clear command must call both relational and graph cleanup
**Warning signs:** Graph queries return stale data after clear + re-index

## Code Examples

Verified patterns from official sources:

### Command Definition with Options
```java
// Source: Spring Shell @Command annotation docs
@Command(command = "index", description = "Index markdown files from a directory")
public String index(
        @Option(longNames = "path", shortNames = 'p', required = true,
                description = "Path to directory containing markdown files")
        String path) {

    Path directory = Path.of(path).toAbsolutePath();
    if (!Files.exists(directory)) {
        return "Error: Directory does not exist: " + path;
    }
    if (!Files.isDirectory(directory)) {
        return "Error: Path is not a directory: " + path;
    }

    ingestionService.ingestDirectory(directory);
    return "Indexing completed for: " + path;
}
```

### Search Command with Formatted Output
```java
// Source: Spring Shell docs + existing SearchService patterns
@Command(command = "search", description = "Search indexed documentation")
public String search(
        @Option(longNames = "query", shortNames = 'q', required = true,
                description = "Search query text")
        String query,
        @Option(longNames = "limit", shortNames = 'n', defaultValue = "5",
                description = "Maximum results to return")
        int limit) {

    List<SearchResult> results = searchService.hybridSearch(query, limit);

    if (results.isEmpty()) {
        return "No results found for: " + query;
    }

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Found %d results for '%s':\n\n", results.size(), query));

    for (int i = 0; i < results.size(); i++) {
        SearchResult r = results.get(i);
        sb.append(String.format("%d. %s\n", i + 1, r.documentTitle()));
        sb.append(String.format("   Path: %s\n", r.documentPath()));
        sb.append(String.format("   Score: %.2f\n", r.similarity()));
        sb.append(String.format("   Excerpt: %s\n\n", truncate(r.childContent(), 100)));
    }

    return sb.toString();
}
```

### Status Command (requires new repository methods)
```java
// Source: Application-specific pattern
@Command(command = "status", description = "Show database status")
public String status() {
    long docCount = documentRepository.count();
    long chunkCount = chunkRepository.count();
    Optional<Instant> lastIndexed = documentRepository.findLastUpdated();

    StringBuilder sb = new StringBuilder();
    sb.append("Alexandria Status\n");
    sb.append("=================\n");
    sb.append(String.format("Documents: %d\n", docCount));
    sb.append(String.format("Chunks: %d\n", chunkCount));
    sb.append(String.format("Last indexed: %s\n",
        lastIndexed.map(Instant::toString).orElse("Never")));

    return sb.toString();
}
```

### Clear Command with Confirmation
```java
// Source: Application-specific pattern
@Command(command = "clear", description = "Clear all indexed data")
public String clear(
        @Option(longNames = "force", shortNames = 'f', defaultValue = "false",
                description = "Skip confirmation prompt")
        boolean force) {

    if (!force) {
        return "Warning: This will delete all indexed data. Use --force to confirm.";
    }

    // Clear PostgreSQL tables
    chunkRepository.deleteAll();
    documentRepository.deleteAll();

    // Clear Apache AGE graph
    graphRepository.clearAll();

    return "All indexed data cleared.";
}
```

### Exit Code Handling
```java
// Source: Spring Shell exception handling docs
@Component
public class CliExceptionResolver implements CommandExceptionResolver {

    @Override
    public CommandHandlingResult resolve(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return CommandHandlingResult.of("Error: " + e.getMessage() + "\n", 1);
        }
        if (e instanceof IOException) {
            return CommandHandlingResult.of("I/O Error: " + e.getMessage() + "\n", 2);
        }
        // Let other exceptions propagate
        return null;
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| @ShellMethod + @ShellOption | @Command + @Option | Spring Shell 3.1+ | Migration recommended, legacy deprecated |
| Interactive shell default | Non-interactive via config | Spring Shell 3.x | Simpler for CLI tools |
| Manual exit codes | CommandHandlingResult | Spring Shell 3.x | Proper automation support |

**Deprecated/outdated:**
- `@ShellMethod` annotation: Will be removed in future versions; use `@Command` instead
- `@ShellOption` annotation: Replaced by `@Option` in new annotation model

## Open Questions

Things that couldn't be fully resolved:

1. **MCP and CLI coexistence**
   - What we know: MCP uses stdio transport, CLI uses stdout for output
   - What's unclear: Best way to separate modes cleanly (profiles? separate JARs?)
   - Recommendation: Use Spring profiles; test both modes don't interfere

2. **Progress feedback during indexing**
   - What we know: Spring Shell has ProgressView component
   - What's unclear: Works in non-interactive mode? Performance overhead?
   - Recommendation: Start with simple console output, upgrade if needed

3. **Repository method additions**
   - What we know: Need `count()` and `findLastUpdated()` for status command
   - What's unclear: Also need `deleteAll()` for clear command
   - Recommendation: Add to port interfaces, implement in JDBC repositories

## Required Repository Additions

Based on CLI requirements, the following new methods are needed:

### DocumentRepository (port interface)
```java
long count();                          // For status command
Optional<Instant> findLastUpdated();   // For status command
void deleteAll();                      // For clear command
```

### ChunkRepository (port interface)
```java
long count();                          // For status command
void deleteAll();                      // For clear command
```

### GraphRepository (port interface)
```java
void clearAll();                       // For clear command
```

## Sources

### Primary (HIGH confidence)
- [Spring Shell Documentation](https://docs.spring.io/spring-shell/reference/execution.html) - Execution modes, runners
- [Spring Shell @Option](https://docs.spring.io/spring-shell/reference/options/basics/annotation.html) - Annotation syntax
- [Spring Shell Positional Args](https://docs.spring.io/spring-shell/reference/options/positional.html) - Positional parameters
- [Spring Shell GitHub](https://github.com/spring-projects/spring-shell) - Version 3.4.1 release info

### Secondary (MEDIUM confidence)
- [Spring Shell Tutorial (mydeveloperplanet)](https://mydeveloperplanet.com/2024/12/04/spring-boot-build-your-own-cli-with-spring-shell/) - Practical examples
- [Spring Shell Build CLI apps (DEV Community)](https://dev.to/noelopez/spring-shell-build-cli-apps-2l1o) - Usage patterns
- [Spring Shell TableBuilder API](https://docs.spring.io/spring-shell/docs/current/api/org/springframework/shell/table/TableBuilder.html) - Table output

### Tertiary (LOW confidence)
- [Spring Shell vs Picocli discussion](https://github.com/spring-projects/spring-shell/discussions/534) - Community comparison

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Spring Shell is the official Spring CLI framework
- Architecture: HIGH - Follows existing Alexandria patterns (api layer, DI)
- Pitfalls: MEDIUM - Based on docs and community discussions, not firsthand experience
- Repository additions: HIGH - Clear from requirements analysis

**Research date:** 2026-01-20
**Valid until:** 2026-02-20 (Spring Shell is stable, 30-day validity)
