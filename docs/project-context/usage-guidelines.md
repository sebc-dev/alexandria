# Usage Guidelines

**For AI Agents:**

- **Read this file before implementing any code** - All rules are mandatory
- **Follow ALL rules exactly as documented** - No exceptions without explicit justification
- **When in doubt, prefer the more restrictive option** - Err on the side of strictness
- **Consult specific sections for context** - Each section addresses different concerns
- **Critical sections to never skip:**
  - MCP Protocol rules (TIER 1 - Bloquant complet)
  - HNSW Index usage (TIER 2 - Critique performance)
  - Architecture Hexagonale boundaries (TIER 1 - Bloquant complet)
- **Update this file if new patterns emerge** - Document learnings for future agents

**For Humans:**

- **Keep this file lean and focused on agent needs** - Only unobvious details that LLMs miss
- **Update when technology stack changes** - Version bumps, new frameworks, breaking changes
- **Review quarterly for outdated rules** - Remove rules that become industry standard
- **Test with real AI agent implementation** - Validate rules prevent actual mistakes
- **Prioritize TIER 1 and TIER 2 rules** - These break production if violated

**Integration Points:**

- **BMAD Workflows:** Auto-loaded in `dev-story`, `quick-dev`, `code-review` workflows
- **Custom Skills:** Reference via `@project-context.md` in skill prompts
- **Sub-Agents:** Include in agent system prompts for consistency
- **Claude Code Sessions:** Explicitly include when starting implementation work

**Maintenance Schedule:**

- **After each epic completion:** Review for new patterns discovered
- **Technology upgrades:** Update versions and breaking changes immediately
- **Quarterly review:** Optimize content, remove obvious rules
- **NFR changes:** Update performance thresholds if requirements evolve

Last Updated: 2025-12-26
