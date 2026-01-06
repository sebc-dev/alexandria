# Phase 10: CLI

- [ ] **Task 29: Create IngestCommand**
  - File: `src/main/java/dev/alexandria/cli/IngestCommand.java`
  - Action: @Component @Command(name="ingest") implementing Runnable. Parameters: path (positional). Options: -r/--recursive, -b/--batch-size (default 25), --dry-run
  - Notes: Uses Picocli spring boot starter. Batch processing with Guava Lists.partition. Filter supported formats (.md, .txt, .html, llms.txt)
