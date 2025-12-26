# CodeRabbit Configuration for Alexandria: Complete Implementation Guide

CodeRabbit provides AI-powered code review that can supplement—but not replace—your existing architecture enforcement tools. For Alexandria's hexagonal architecture, the optimal strategy combines **CodeRabbit for AI-contextual review**, **ts-arch for hard architecture enforcement**, and **ESLint for real-time validation**. With proper configuration targeting the **≤2 comments per commit** goal, CodeRabbit can focus exclusively on critical issues while your deterministic tools handle architectural boundaries.

---

## CodeRabbit capabilities as of December 2025

CodeRabbit has evolved significantly in 2024-2025, now integrating **40+ static analysis tools** alongside its GPT-4o and Claude 3.5 Sonnet AI engines. Key features include committable one-click suggestions, cross-file code graph analysis (April 2025), automatic web queries for documentation lookup, and a learnings system that improves reviews based on team feedback.

**Supported languages and frameworks** include full TypeScript support with ESLint integration, though notably **type-checking rules are disabled** for security and performance. The platform reads `.eslintrc` configurations directly, using a curated allow-list of plugins including `@typescript-eslint`, `import`, `promise`, and `unicorn`. For Alexandria's stack, CodeRabbit officially supports projects using the Bun runtime—Bun's own repository uses CodeRabbit for reviews—though no Bun-specific configuration options exist since CodeRabbit performs static analysis rather than runtime execution.

Recent additions particularly relevant to Alexandria include **AST-grep integration** for custom pattern matching (enabling import restriction rules), **Code Guidelines** that automatically read from `CLAUDE.md` files, and **path-based review instructions** that enable layer-specific enforcement. The May 2025 release added VS Code and Cursor IDE extensions, while April 2025 brought agentic planning capabilities for complex review tasks.

---

## Configuration file deep dive

The `.coderabbit.yaml` configuration file controls all review behavior. Place this file in your repository root; CodeRabbit validates it against the schema at `https://coderabbit.ai/integrations/schema.v2.json`.

**Profile selection** is the single most impactful setting for comment reduction. Setting `profile: "chill"` reduces nitpicky feedback by approximately 40% compared to `"assertive"`, making it essential for the ≤2 comments target. Combined with `tone_instructions`, you can direct the AI to focus exclusively on critical issues.

**Path-based instructions** enable different review rules per directory—critical for hexagonal architecture where domain, ports, and adapters have distinct requirements. The `path_instructions` array accepts glob patterns and multiline instruction strings that the AI interprets during review. Path filters (`path_filters`) use exclusion patterns prefixed with `!` to skip generated code, tests, or vendor directories entirely.

**AST-grep rules** provide pattern-matching capabilities for custom code validation. Unlike AI-based instructions, these are deterministic—they will consistently flag violations like forbidden imports or missing readonly modifiers. Rules are defined in YAML files within a `rules/` directory and referenced in the configuration.

---

## Hexagonal architecture enforcement strategy

CodeRabbit lacks native architecture validation—it cannot build dependency graphs or enforce layer boundaries deterministically. For Alexandria's **non-negotiable** hexagonal architecture constraint, implement a three-tier enforcement strategy:

**Tier 1: ts-arch for hard enforcement** runs as unit tests in your CI pipeline, failing builds when domain imports from adapters. Create architecture tests using ts-arch's fluent API:

```typescript
// architecture.spec.ts
import "tsarch/dist/jest"
import { filesOfProject } from "tsarch"

describe("Hexagonal Architecture", () => {
  it("domain must not depend on adapters", async () => {
    const rule = filesOfProject()
      .inFolder("domain")
      .shouldNot()
      .dependOnFiles()
      .inFolder("adapters")
    await expect(rule).toPassAsync()
  })

  it("domain must not import Zod", async () => {
    const rule = filesOfProject()
      .inFolder("domain")
      .shouldNot()
      .dependOnFiles()
      .matchingPattern(".*zod.*")
    await expect(rule).toPassAsync()
  })
})
```

**Tier 2: ESLint `import/no-restricted-paths`** provides real-time IDE feedback and pre-commit blocking. Configure zones that prevent cross-layer imports:

```javascript
// .eslintrc.js
module.exports = {
  rules: {
    'import/no-restricted-paths': ['error', {
      zones: [
        { target: './src/domain', from: './src/adapters', 
          message: 'Domain cannot import from adapters' },
        { target: './src/ports', from: './src/adapters', 
          message: 'Ports cannot import from adapters' }
      ]
    }]
  }
}
```

**Tier 3: CodeRabbit for contextual review** catches subtle violations that tools miss and provides explanatory feedback for new team members. Path instructions tell the AI what to flag in each layer.

---

## Complete .coderabbit.yaml for Alexandria

```yaml
# yaml-language-server: $schema=https://coderabbit.ai/integrations/schema.v2.json
language: "en-US"
early_access: false

tone_instructions: |
  You are reviewing a RAG system built with strict hexagonal architecture.
  Focus ONLY on: architecture violations, security issues, and correctness bugs.
  Do not comment on: formatting, minor style preferences, or suggestions that are "nice to have."
  Be extremely concise—one sentence maximum per comment.
  If unsure whether to comment, do not comment.

reviews:
  profile: "chill"
  request_changes_workflow: false
  high_level_summary: true
  poem: false
  review_status: false
  collapse_walkthrough: true
  sequence_diagrams: false
  
  auto_review:
    enabled: true
    drafts: false
    ignore_title_keywords: ["wip", "WIP", "draft", "Draft", "DO NOT MERGE"]
    base_branches: ["main", "develop"]

  path_filters:
    - "!dist/**"
    - "!node_modules/**"
    - "!**/*.spec.ts"
    - "!**/*.test.ts"
    - "!**/fixtures/**"
    - "!drizzle/**"

  path_instructions:
    - path: "src/domain/**/*.ts"
      instructions: |
        DOMAIN LAYER - Strict hexagonal architecture enforcement:
        
        🚫 CRITICAL VIOLATIONS (always flag):
        - Any import from 'adapters/', 'infrastructure/', or '@/adapters'
        - Any import of 'zod', 'hono', 'drizzle-orm', or framework code
        - Properties without 'readonly' modifier (immutability required)
        - Setter methods (violates immutability)
        - Bare 'throw new Error()' - must use custom domain errors
        - .then() chains - must use async/await
        - Classes not using PascalCase
        - Custom errors not ending with 'Error' suffix
        
        ✅ ALLOWED:
        - Imports from other domain modules
        - Imports from ports/ (interfaces only)
        - Pure TypeScript types and utilities
        
    - path: "src/ports/**/*.ts"
      instructions: |
        PORTS LAYER - Interface definitions:
        
        🚫 CRITICAL VIOLATIONS:
        - Any import from 'adapters/' or 'infrastructure/'
        - Interfaces not ending with 'Port' suffix
        - Concrete implementations (ports must be abstract)
        
        ✅ ALLOWED:
        - Imports from domain/ (entity types, value objects)
        - Pure TypeScript interfaces
        
    - path: "src/adapters/**/*.ts"
      instructions: |
        ADAPTERS LAYER - Implementation code:
        
        Focus on:
        - Verify adapter implements corresponding port interface
        - Check Zod schemas validate at boundaries
        - Ensure proper error translation to domain errors
        - Validate Drizzle ORM usage patterns
        
        Light review only—infrastructure code has more flexibility.
        
    - path: "src/application/**/*.ts"
      instructions: |
        APPLICATION LAYER - Use cases:
        
        Check for:
        - camelCase function and variable names
        - Async/await usage (no .then() chains)
        - Proper dependency injection via ports
        - No direct adapter imports

  tools:
    eslint:
      enabled: true
    ast-grep:
      essential_rules: true
      rule_dirs:
        - ".coderabbit/rules"
    biome:
      enabled: false
    oxc:
      enabled: false
    gitleaks:
      enabled: true

knowledge_base:
  code_guidelines:
    enabled: true
    filePatterns:
      - "**/CLAUDE.md"
      - "**/ARCHITECTURE.md"
  learnings:
    scope: "auto"
  web_search:
    enabled: true

chat:
  auto_reply: true
```

---

## AST-grep custom rules

Create a `.coderabbit/rules/` directory with these deterministic pattern rules:

**no-then-chains.yaml** enforces async/await:
```yaml
id: no-then-chains
language: typescript
message: "Use async/await instead of .then() chains"
severity: warning
rule:
  pattern: $PROMISE.then($$$)
```

**no-bare-throws.yaml** requires custom errors:
```yaml
id: no-bare-throws
language: typescript
message: "Use custom domain errors instead of bare Error"
severity: error
rule:
  pattern: throw new Error($MSG)
```

**no-domain-adapter-import.yaml** enforces layer boundaries:
```yaml
id: no-domain-adapter-import
language: typescript
message: "ARCHITECTURE VIOLATION: Domain cannot import from adapters"
severity: error
files:
  - "src/domain/**/*.ts"
rule:
  kind: import_statement
  has:
    kind: string
    regex: "(adapters|infrastructure)"
```

**no-zod-in-domain.yaml** restricts validation libraries:
```yaml
id: no-zod-in-domain
language: typescript
message: "Zod is forbidden in domain layer"
severity: error
files:
  - "src/domain/**/*.ts"
rule:
  kind: import_statement
  has:
    kind: string_fragment
    regex: "^zod$"
```

---

## Integration with ts-arch, ESLint, and Prettier

**ESLint integration** works automatically—CodeRabbit reads your `.eslintrc` configuration. However, TypeScript ESLint type-checking rules are disabled in CodeRabbit's sandboxed environment. This means rules like `@typescript-eslint/no-floating-promises` won't run through CodeRabbit; run these in your CI pipeline instead. Disable CodeRabbit's ESLint if you prefer CI-only linting:

```yaml
reviews:
  tools:
    eslint:
      enabled: false  # Let CI handle ESLint
```

**Prettier compatibility** requires no special configuration—CodeRabbit respects `eslint-config-prettier` if present. The `profile: "chill"` setting also reduces formatting-related comments. Add explicit instructions if needed: "Do not comment on formatting—Prettier handles this."

**ts-arch runs independently** in your test suite. CodeRabbit has no direct integration, but both tools serve complementary roles: ts-arch provides deterministic build-breaking enforcement, while CodeRabbit offers AI-powered explanatory feedback.

**Deduplication strategy**: If running ESLint in both CI and CodeRabbit, you'll see duplicate warnings. Choose one approach—either let CodeRabbit handle ESLint (simpler) or disable CodeRabbit's ESLint and run it in CI (more control over exact versions and plugins).

---

## Achieving ≤2 comments per commit

Real-world data from projects using CodeRabbit shows approximately **35% of comments are quality improvements** while **36% are nitpicks or noise**. The `chill` profile combined with focused instructions dramatically improves this ratio.

**Configuration levers** that reduce comment volume:
- `profile: "chill"` reduces nitpicky feedback (~40% reduction)
- `poem: false` and `review_status: false` eliminate decorative comments
- `collapse_walkthrough: true` reduces visual noise
- `tone_instructions` directing focus to critical issues only
- `path_filters` excluding tests, fixtures, and generated code

**Behavioral patterns** that help:
- Keep PRs under 400 lines—AI review quality degrades on large diffs
- Reply to false positives with explanations; CodeRabbit learns from feedback
- Use `@coderabbitai resolve` to batch-dismiss noise
- Link issues to PRs for better contextual understanding

---

## Pricing and performance considerations

**Open-source repositories** receive full Pro tier features at no cost—unlimited reviews with all 40+ static analysis tools. **Private repositories** require Pro ($24/month annual, $30/month) for full reviews beyond the 14-day trial.

**Rate limits apply** across all tiers: 200 files reviewed per hour, 3 back-to-back reviews then 4/hour, 25 back-to-back conversations then 50/hour. These limits rarely impact typical development workflows.

**Performance impact** is minimal—CodeRabbit runs in parallel with your CI pipeline rather than blocking it. Reviews typically complete within 1-3 minutes. Enable `abort_on_close: true` to stop processing closed PRs, and avoid setting CodeRabbit as a required check to prevent merge blocking.

---

## Step-by-step setup for Alexandria

**Step 1**: Install CodeRabbit from [GitHub Marketplace](https://github.com/marketplace/coderabbitai). Grant permissions for Pull Requests (read-write) and Contents (read-write).

**Step 2**: Create `.coderabbit.yaml` in your repository root using the configuration above.

**Step 3**: Create `.coderabbit/rules/` directory and add the AST-grep rules for pattern enforcement.

**Step 4**: Create `CLAUDE.md` in your repository root documenting your hexagonal architecture rules—CodeRabbit automatically reads this for context:

```markdown
# Alexandria Architecture

## Hexagonal Architecture (NON-NEGOTIABLE)

### Layer Dependencies
- Domain → Ports ✓ (types only)
- Adapters → Ports → Domain ✓
- Domain → Adapters ✗ FORBIDDEN

### Naming Conventions
- Entities: PascalCase
- Variables: camelCase
- Ports: suffix "Port"
- Errors: suffix "Error"

### Patterns Required
- All entity properties: readonly
- Error handling: custom domain errors only
- Async code: async/await (no .then())
- Validation: Zod in adapters layer only
```

**Step 5**: Install ts-arch and create architecture tests in `src/architecture.spec.ts` for hard enforcement.

**Step 6**: Configure ESLint `import/no-restricted-paths` for real-time IDE feedback.

**Step 7**: Open a test PR and run `@coderabbitai configuration` to verify settings are applied correctly. Iterate on `path_instructions` based on initial review quality.

---

## Conclusion

CodeRabbit excels at contextual, AI-powered review but requires deterministic tools for hard architectural boundaries. The recommended Alexandria setup uses **ts-arch as the authoritative source** for hexagonal architecture enforcement (failing builds on violations), **ESLint for developer-time feedback**, and **CodeRabbit for intelligent supplementary review** focused on subtle issues, security, and team education.

The configuration prioritizes the ≤2 comments target through aggressive filtering, focused path instructions, and the `chill` profile. AST-grep rules add deterministic pattern checking for async/await and error handling conventions. For open-source Alexandria development, this entire setup runs at zero cost; private repositories require the $24/month Pro tier for full functionality.