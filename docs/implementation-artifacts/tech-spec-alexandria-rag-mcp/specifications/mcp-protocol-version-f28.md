# MCP Protocol Version (F28)

**Versions utilisées:**

| Composant | Version | Notes |
|-----------|---------|-------|
| MCP Protocol | 2025-11-25 | Version actuelle (1er anniversaire MCP) |
| @modelcontextprotocol/sdk | 1.22.x | SDK stable production (^1.22.0) |

**⚠️ Attention:**
- SDK 2.0.0 **n'existe pas** (pré-alpha sur branche main, release Q1 2026)
- Utiliser impérativement v1.x en production
- La version 2024-11-05 du protocole reste supportée pour rétrocompatibilité

**Nouveautés 2025-11-25:**
- Tasks primitives
- Extensions framework
- OAuth 2.1 amélioré

**Configuration serveur:**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

const server = new McpServer({
  name: 'alexandria',
  version: pkg.version,
  // protocolVersion est géré par le SDK
});

// Pattern recommandé v1.22+
server.registerTool("search", {
  description: "Recherche sémantique",
  inputSchema: { query: z.string() }
}, async ({ query }) => ({
  content: [{ type: "text", text: "résultats" }]
}));

await server.connect(new StdioServerTransport());
```

**Note Zod:** Utiliser `import { z } from "zod/v3"` pour compatibilité MCP (issue #1429 - Zod v4 incompatible).
