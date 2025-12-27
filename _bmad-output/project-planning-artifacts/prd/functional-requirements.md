# Functional Requirements

## Convention & Documentation Management

- **FR1**: Users can upload convention documents in markdown format to the knowledge base
- **FR2**: Users can upload technical documentation in markdown format to the knowledge base
- **FR3**: Users can manually specify document type (convention vs documentation) during upload
- **FR4**: Users can list all conventions and documentations with filtering options
- **FR5**: Users can filter documents by type (convention, documentation, or all)
- **FR6**: Users can filter documents by associated technology
- **FR7**: Users can filter documents by project identifier
- **FR8**: Users can view complete content of any stored document
- **FR9**: Users can view document metadata (type, technologies, project, creation date)
- **FR10**: Users can delete conventions or documentations from the knowledge base
- **FR11**: Users can access an interactive CRUD menu for all document operations
- **FR12**: System can confirm deletion operations before permanent removal
- **FR13**: System can cascade delete associated embeddings when document is removed
- **FR14**: System can cascade delete technology links when document is removed
- **FR15**: System can parse markdown files and extract content for storage

## Active Compliance Filter (3-Layer RAG Architecture)

- **FR16**: System can perform semantic vector search on conventions (Layer 1)
- **FR17**: System can retrieve conventions based on query similarity using pgvector
- **FR18**: System can present conventions as "non-negotiable laws" to the AI agent
- **FR19**: System can automatically link technical documentation to conventions via technology identifiers (Layer 2)
- **FR20**: System can retrieve documentation associated with convention technologies using SQL JOIN
- **FR21**: System can contextualize documentation based on Layer 1 convention rules
- **FR22**: System can invoke LLM reformulation agent to fuse conventions and documentation (Layer 3)
- **FR23**: System can eliminate contradictory patterns between conventions and documentation
- **FR24**: System can eliminate obsolete syntax in favor of current best practices
- **FR25**: System can generate a unified implementation guide from multi-source context
- **FR26**: System can validate structural coherence of fused context
- **FR27**: System can optimize context for AI agent consumption
- **FR28**: System can ensure Layer 3 output is mono-approach (single recommended path)

## Context Retrieval & Delivery

- **FR29**: System can receive contextual queries from Claude Code agents
- **FR30**: System can generate vector embeddings for search queries
- **FR31**: System can retrieve relevant conventions based on semantic similarity
- **FR32**: System can identify technology identifiers from retrieved conventions
- **FR33**: System can retrieve all documentation linked to identified technologies
- **FR34**: System can aggregate conventions and documentation before reformulation
- **FR35**: System can deliver fused context to Claude Code agents
- **FR36**: System can deliver context optimized for code generation tasks
- **FR37**: System can deliver context optimized for documentation generation tasks
- **FR38**: System can maintain context relevance throughout retrieval pipeline

## Code Validation & Conformity

- **FR39**: System can validate generated code against project conventions
- **FR40**: System can detect convention violations in code snippets
- **FR41**: System can detect non-conformities in code structure
- **FR42**: System can generate conformity reports with detected violations
- **FR43**: System can provide correction suggestions for violations
- **FR44**: System can calculate conformity scores for code submissions
- **FR45**: System can compare code patterns against convention rules
- **FR46**: System can identify missing required patterns from conventions
- **FR47**: Users can request explicit validation of code snippets
- **FR48**: Users can validate code at file level
- **FR49**: System can report validation results with detailed violation descriptions

## Claude Code Integration

- **FR50**: Skills can be auto-invoked by Claude Code during code generation
- **FR51**: Skills can retrieve context transparently without user intervention
- **FR52**: Skills can invoke MCP tools for context retrieval
- **FR53**: Skills can delegate to reformulation sub-agent
- **FR54**: Skills can return fused context to Claude Code
- **FR55**: Users can invoke validation skills manually via slash commands
- **FR56**: Users can query Alexandria context using slash commands
- **FR57**: Users can configure Alexandria using interactive slash commands
- **FR58**: Users can list documents using slash commands with optional parameters
- **FR59**: Users can read document content using slash commands
- **FR60**: Users can delete documents using slash commands
- **FR61**: Sub-agents can receive conventions and documentation as input
- **FR62**: Sub-agents can analyze contradictions between multiple sources
- **FR63**: Sub-agents can produce coherent unified guides
- **FR64**: Sub-agents can operate autonomously without user intervention
- **FR65**: Sub-agents can use economical LLM models (Haiku 3.5) for cost optimization
- **FR66**: MCP Server can expose tools via Model Context Protocol
- **FR67**: MCP Server can handle retrieve context requests
- **FR68**: MCP Server can handle validate code requests
- **FR69**: MCP Server can handle upload convention requests
- **FR70**: MCP Server can handle list projects requests
- **FR71**: MCP Server can handle list documents requests
- **FR72**: MCP Server can handle read document requests
- **FR73**: MCP Server can handle delete document requests
- **FR74**: MCP Server can communicate with Claude Code using MCP protocol

## Project & Technology Configuration

- **FR75**: Users can configure multiple independent projects
- **FR76**: Users can assign unique identifiers to projects
- **FR77**: Users can declare multiple technologies per convention
- **FR78**: Users can associate multiple technologies with documentation (exemple: FastAPI doc → ["python", "fastapi"])
- **FR79**: System can create technology-convention associations via pivot table
- **FR80**: System can maintain technology-documentation relationships
- **FR81**: System can retrieve conventions for specific project identifiers
- **FR82**: System can retrieve all conventions associated with specific technologies
- **FR83**: System can retrieve all documentation for specific technologies
- **FR84**: Users can list all configured projects with metadata
- **FR85**: Users can view technology configuration per project
- **FR86**: Users can view convention count per project
- **FR87**: Users can view documentation count per technology
- **FR88**: System can store and manage conventions for any programming language used by user projects (Python, TypeScript, Java, Groovy, Go, Rust, etc.)
- **FR89**: System can store and manage documentation for any framework used by user projects (FastAPI, Spring, NestJS, Django, Express, etc.)
- **FR90**: Users can onboard new projects with technology setup
- **FR91**: System can maintain document-project associations for multi-project sharing
- **FR104**: Users can associate documents (conventions or documentation) with multiple projects
- **FR105**: System can retrieve documents shared across multiple projects
- **FR106**: System can maintain project-document associations via pivot table

## Testing, Debugging & Observability

- **FR92**: Users can test retrieval queries with debug output
- **FR93**: System can display Layer 1 retrieval results (conventions found)
- **FR94**: System can display Layer 2 retrieval results (linked documentation)
- **FR95**: System can display Layer 3 reformulation output (fused guide)
- **FR96**: System can display relevance metrics for retrieved content
- **FR97**: System can display response time metrics
- **FR98**: System can log all retrieval requests with timestamps
- **FR99**: System can log all validation requests with results
- **FR100**: System can log upload operations with document metadata
- **FR101**: Users can manually track success metrics (interventions, commits, reviews)
- **FR102**: System can provide visibility into retrieval pipeline for debugging
- **FR103**: System can validate setup effectiveness before production use
