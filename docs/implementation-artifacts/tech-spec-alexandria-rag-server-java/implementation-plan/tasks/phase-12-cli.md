# Phase 12: CLI (TDD)

- [ ] **Task 33: Create IngestCommand** (TDD)
  - **RED**:
    - Test file: `src/test/java/dev/alexandria/cli/IngestCommandTest.java`
    - Test cases:
      - `shouldIngestSingleFile()` - positional path argument
      - `shouldIngestDirectoryRecursively()` - -r flag
      - `shouldRespectBatchSize()` - -b/--batch-size option
      - `shouldFilterSupportedFormats()` - only .md, .txt, .html, llms.txt
      - `shouldPerformDryRun()` - --dry-run shows what would happen
      - `shouldReportProgress()` - progress output
      - `shouldExitWithErrorOnFailure()` - non-zero exit code
  - **GREEN**:
    - File: `src/main/java/dev/alexandria/cli/IngestCommand.java`
    - Action: @Component @Command(name="ingest") implementing Runnable
  - Notes: Picocli spring boot starter. Guava Lists.partition for batches
