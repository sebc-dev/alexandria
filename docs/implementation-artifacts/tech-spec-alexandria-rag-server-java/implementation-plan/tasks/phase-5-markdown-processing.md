# Phase 5: Markdown Processing

- [ ] **Task 17: Create AlexandriaMarkdownSplitter**
  - File: `src/main/java/dev/alexandria/core/AlexandriaMarkdownSplitter.java`
  - Action: Implements DocumentSplitter. Extracts YAML front matter, protects code blocks/tables with placeholders, splits by headers then recursively, restores protected content, adds breadcrumb metadata
  - Notes: MAX_CHUNK_TOKENS=500, OVERLAP_TOKENS=75, MAX_OVERSIZED_TOKENS=1500, BREADCRUMB_DEPTH=3. Uses CommonMark-java for parsing

- [ ] **Task 18: Create LlmsTxtParser**
  - File: `src/main/java/dev/alexandria/core/LlmsTxtParser.java`
  - Action: Standalone parser (not DocumentParser) for llms.txt format. Parses title, description, sections with links. Returns LlmsTxtDocument record with nested LlmsTxtSection and LlmsTxtLink records
  - Notes: Format spec at llmstxt.org. Regex patterns for title, blockquote, section (H2), links
