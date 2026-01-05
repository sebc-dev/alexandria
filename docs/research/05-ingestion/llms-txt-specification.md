# llms.txt Standard: Complete Specification for Java Parser Implementation

**Status**: ✅ Research Complete — Specification fully documented, no existing Java parsers found

The llms.txt standard is a convention-based Markdown format for providing LLM-readable documentation at inference time. Created by Jeremy Howard (Answer.AI) in September 2024, it has gained adoption from Anthropic, Cloudflare, Svelte, and ~2,000 domains. **No Java/JVM parser exists**, making Alexandria's implementation the first in the ecosystem.

---

## Complete format specification

The llms.txt format uses a strict subset of Markdown with sections in a **mandatory order**. The only required element is the H1 title—everything else is optional but positionally constrained.

### Canonical structure

```markdown
# Project Title                           ← REQUIRED: Single H1

> Brief summary with key information      ← OPTIONAL: Blockquote

Freeform content providing context.       ← OPTIONAL: Any markdown EXCEPT headings
Can include paragraphs, lists, emphasis,
code blocks. No H2-H6 allowed here.

## Section Name                           ← OPTIONAL: H2 starts file list section

- [Link Title](https://url): Description  ← File list: markdown links
- [Another Link](https://url)             ← Description after colon is optional

## Optional                               ← SPECIAL: Section excluded from compact context

- [Extended Resource](https://url): Can be skipped for shorter context
```

### Parsing rules by element

| Element | Pattern | Required | Notes |
|---------|---------|----------|-------|
| **Title** | `^# (.+)$` | **Yes** | Single H1, first line after trimming |
| **Summary** | `^> (.+)$` | No | Single blockquote line after title |
| **Info** | Free text | No | All content before first H2, no headings allowed |
| **Sections** | `^## (.+)$` | No | Zero or more H2 headers define sections |
| **Links** | `-\s*\[title\]\(url\)(?::\s*desc)?` | No | Markdown list items with links |

### The definitive link parsing regex

From the official Python implementation:
```regex
-\s*\[(?P<title>[^\]]+)\]\((?P<url>[^\)]+)\)(?::\s*(?P<desc>.*))?
```

This handles:
- Whitespace after list marker (`-` or `*`)
- Link text in brackets
- URL in parentheses  
- Optional colon-separated description

---

## llms.txt versus llms-full.txt

These serve fundamentally different purposes in the LLM context loading workflow:

| File | Purpose | Content | Context Impact |
|------|---------|---------|----------------|
| **llms.txt** | Navigation index | Links to resources with brief descriptions | Small (~2-5KB typical) |
| **llms-full.txt** | Complete content | All documentation concatenated | Large (can exceed context limits) |

**llms-full.txt** is a community extension popularized by Mintlify, not part of Jeremy Howard's original spec. It eliminates the need for LLMs to follow links by including full page content inline.

Additional variants observed in production:

- **llms-ctx.txt** — XML-formatted expansion of llms.txt, excludes `## Optional` section
- **llms-ctx-full.txt** — XML-formatted expansion including `## Optional`
- **llms-medium.txt** / **llms-small.txt** — Compressed versions for limited context windows (Svelte uses this)

The `## Optional` section carries **semantic meaning**: tools like `llms_txt2ctx` exclude it when generating compact context, making it ideal for secondary references.

---

## Formal schema status

**No formal schema exists.** The specification is entirely convention-based with these characteristics:

- **Normative**: H1 title required, section order fixed, link format standardized
- **Optional**: Everything after H1 (summary, info, sections, descriptions)
- **Implicit**: UTF-8 encoding, `text/markdown` MIME type
- **Undefined**: Multiple H1 handling, case sensitivity of "Optional", H3-H6 behavior in sections

The reference implementation is the Python code itself—specifically the `parse_llms_txt()` function in ~20 lines of regex parsing.

---

## Metadata extraction for Alexandria RAG indexing

Based on existing parser implementations, extract this structured data:

```java
public record LlmsTxtDocument(
    String title,                          // Required - from H1
    String summary,                        // Nullable - from blockquote
    String info,                           // May be empty - freeform content before sections
    Map<String, List<Link>> sections,      // Section name → list of links
    boolean hasOptionalSection             // Flag for context generation decisions
) {}

public record Link(
    String title,                          // Link text in brackets
    String url,                            // URL in parentheses
    String description                     // Nullable - text after colon
) {}
```

### Indexing recommendations for semantic search

For RAG purposes, consider these indexing strategies:

1. **Title + Summary** — Primary document identification, high weight
2. **Info section** — Rich context about the project, medium weight
3. **Section names** — Category metadata for filtering
4. **Link titles + descriptions** — Chunk-level content for retrieval
5. **Full URLs** — Enable fetching linked content for deeper indexing

---

## Existing parser implementations

### No Java/JVM implementations exist

After exhaustive search, **no Java, Kotlin, Scala, or Clojure parsers** were found. Alexandria will be the first JVM implementation.

### Reference implementations by language

| Language | Repository | Approach | Notes |
|----------|------------|----------|-------|
| **Python** | `AnswerDotAI/llms-txt` | Regex | Official reference, 20 LOC core parser |
| **Rust** | `plaguss/llms-txt-rs` | Regex + PyO3 | Python bindings, same data model |
| **PHP** | `raphaelstolt/llms-txt-php` | OOP + validation | Builder pattern, error collection |
| **JavaScript** | llmstxt.org sample | Regex | Demo implementation only |

The Python implementation's parsing algorithm:
```python
# 1. Split on H2 headers to separate sections
start, *rest = re.split(r'^##\s*(.*?$)', txt, flags=re.MULTILINE)

# 2. Parse header section (title, summary, info)
pat = r'^#\s*(?P<title>.+?$)\n+(?:^>\s*(?P<summary>.+?$)$)?\n+(?P<info>.*)'
d = re.search(pat, start.strip(), (re.MULTILINE|re.DOTALL)).groupdict()

# 3. Parse each section's links
link_pat = r'-\s*\[(?P<title>[^\]]+)\]\((?P<url>[^\)]+)\)(?::\s*(?P<desc>.*))?'
```

---

## Java implementation recommendations

### Recommended parsing approach

Use a **hybrid strategy**: regex for link extraction, line-by-line state machine for structure:

```java
public class LlmsTxtParser {
    private static final Pattern H1_PATTERN = Pattern.compile("^#\\s+(.+)$");
    private static final Pattern H2_PATTERN = Pattern.compile("^##\\s+(.+)$");
    private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("^>\\s*(.+)$");
    private static final Pattern LINK_PATTERN = Pattern.compile(
        "-\\s*\\[([^\\]]+)\\]\\(([^\\)]+)\\)(?::\\s*(.*))?");
    
    public LlmsTxtDocument parse(String content) {
        // State machine: TITLE → SUMMARY → INFO → SECTIONS
    }
}
```

### Edge cases and gotchas

| Edge Case | Handling Recommendation |
|-----------|------------------------|
| Missing summary (no blockquote) | Return null, don't fail |
| Empty info section | Return empty string |
| Links without descriptions | Description = null |
| Multiple consecutive newlines | Normalize to single newline |
| URLs containing parentheses | Use greedy matching or count brackets |
| UTF-8 BOM prefix | Strip `\uFEFF` before parsing |
| Windows line endings | Normalize `\r\n` → `\n` |
| "Optional" case sensitivity | Treat as case-sensitive per convention |
| Multiple H1 headers | Use first, log warning |
| H3-H6 in sections | Treat as regular content (ambiguous in spec) |

### Library recommendations

- **flexmark-java** — Full Markdown AST if you need to extract formatted content from info section
- **Pure regex** — Sufficient for llms.txt structure; keeps dependencies minimal
- **commonmark-java** — Lighter alternative if AST needed

For a RAG server focused on indexing, **pure regex is recommended** for simplicity and performance.

---

## Real-world implementation examples

### FastHTML (canonical example)
```markdown
# FastHTML

> FastHTML is a python library which brings together Starlette, Uvicorn, HTMX, and fastcore's `FT` "FastTags" into a library for creating server-rendered hypermedia applications.

Important notes:
- Although parts of its API are inspired by FastAPI, it is *not* compatible with FastAPI syntax
- FastHTML is compatible with JS-native web components and any vanilla JS library, but not with React, Vue, or Svelte

## Docs

- [FastHTML quick start](https://fastht.ml/docs/tutorials/quickstart_for_web_devs.html.md): A brief overview of many FastHTML features
- [HTMX reference](https://github.com/bigskysoftware/htmx/blob/master/www/content/reference.md): Brief description of all HTMX attributes

## Optional

- [Starlette full documentation](https://gist.githubusercontent.com/.../starlette-sml.md): Subset useful for FastHTML development
```

### Svelte (tiered context approach)
Svelte provides multiple compression levels:
- `/llms.txt` — Navigation index
- `/llms-full.txt` — Complete docs
- `/llms-medium.txt` — Medium context windows  
- `/llms-small.txt` — Highly compressed
- `/docs/svelte/llms.txt` — Package-level files

### Cloudflare (extensive documentation)
Over 100 sections covering all products, with both `llms.txt` and `llms-full.txt` variants, plus per-page Markdown exports.

---

## Impact on Alexandria

### Implementation scope

1. **Core parser**: ~100-150 lines Java, regex-based, returns `LlmsTxtDocument` record
2. **Validation**: Title required, warn on structural anomalies
3. **Link expansion**: Fetch linked `.md` files for full-text indexing
4. **Context generation**: Support excluding `## Optional` section

### Integration with Langchain4j

```java
// Example integration pattern
public class LlmsTxtDocumentLoader implements DocumentLoader {
    private final LlmsTxtParser parser = new LlmsTxtParser();
    
    @Override
    public List<Document> load(String source) {
        LlmsTxtDocument doc = parser.parse(fetchContent(source));
        
        // Create searchable chunks from:
        // 1. Title + summary as header document
        // 2. Info section as context document  
        // 3. Each link as individual chunk with metadata
        return createChunks(doc);
    }
}
```

### Recommended chunking strategy

- **Document-level**: Title + summary + info as single chunk for overview queries
- **Section-level**: Each H2 section as chunk with section name metadata
- **Link-level**: Each link entry as mini-chunk for precise retrieval
- **Expanded content**: Optionally fetch linked URLs and chunk those separately

### Priority implementation order

1. Core parser with regex (day 1)
2. Document model records (day 1)
3. Basic validation (day 2)
4. Langchain4j DocumentLoader adapter (day 2)
5. Link content fetcher for deep indexing (day 3)
6. Context generation with Optional exclusion (day 3+)

---

## Conclusion

The llms.txt standard is a **pragmatic, convention-based format** that prioritizes simplicity over formal rigor. Its ~20-line reference implementation demonstrates that parsing is straightforward—the Alexandria parser should require **under 200 lines of Java** with no external Markdown library dependency.

Key implementation insights: treat only H1 as required, use regex for link extraction, normalize line endings before parsing, and consider the `## Optional` section's semantic meaning for context generation. Alexandria will become the **first JVM implementation** of this increasingly adopted standard.