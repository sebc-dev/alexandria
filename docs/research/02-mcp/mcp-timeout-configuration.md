# MCP timeout environment variables in Claude Code: one documented, one not

**MCP_TIMEOUT is officially documented; MCP_TOOL_TIMEOUT is not.** Anthropic's official Claude Code documentation confirms `MCP_TIMEOUT` as a valid environment variable for configuring MCP server **startup** timeouts, but `MCP_TOOL_TIMEOUT` does not appear in any official documentation and is effectively ignored by Claude Code. This distinction matters because the documented variable controls connection initialization, not tool execution duration—a critical gap for projects like Alexandria RAG Server that need to manage long-running MCP operations.

## MCP_TIMEOUT is documented but has significant limitations

Official Anthropic documentation at **code.claude.com/docs/en/mcp** and the localized documentation at **docs.anthropic.com/ko/docs/claude-code/mcp** explicitly describe the `MCP_TIMEOUT` environment variable. The documentation states: "Configure MCP server startup timeout using the MCP_TIMEOUT environment variable (e.g., `MCP_TIMEOUT=10000 claude` sets a 10-second timeout)." The value is specified in **milliseconds**.

However, a critical bug exists (GitHub issue #7575): Claude Code does **not honor MCP_TIMEOUT values exceeding 60 seconds**. Debug logs show that even when setting `MCP_TIMEOUT=100000` (100 seconds), connections consistently timeout after exactly 60,031ms. This hard cap remains unresolved as of September 2025, making the variable only partially functional for extended timeout scenarios.

The CHANGELOG for Claude Code confirms when this feature was added: "MCP server startup timeout can now be configured via MCP_TIMEOUT environment variable" alongside the note that "MCP server startup no longer blocks the app from starting."

## MCP_TOOL_TIMEOUT is not documented and does not work

Extensive searches across docs.anthropic.com, support.anthropic.com, code.claude.com, the Claude Code GitHub repository, and official Anthropic blog posts returned **no results for MCP_TOOL_TIMEOUT**. This variable appears only in GitHub issues where users attempt to use it based on intuitive naming conventions.

GitHub issue #3033 demonstrates users attempting to configure both variables in `~/.claude/settings.json`:

```json
{
  "env": {
    "MCP_TIMEOUT": "60000",
    "MCP_TOOL_TIMEOUT": "120000"
  }
}
```

The issue reporter confirmed these settings were **completely ignored**—environment variable inspection via `cat /proc/$(pgrep claude)/environ | grep MCP` returned empty results. Issue #5221 is explicitly a **feature request** to add MCP_TOOL_TIMEOUT support, confirming it does not currently exist as a functional feature.

## Official environment variables for Claude Code MCP configuration

Only two MCP-specific environment variables are officially documented:

| Variable | Purpose | Default | Example |
|----------|---------|---------|---------|
| `MCP_TIMEOUT` | Server startup/connection timeout | ~60 seconds (hard cap) | `MCP_TIMEOUT=10000 claude` |
| `MAX_MCP_OUTPUT_TOKENS` | Maximum tokens in MCP tool output | 25,000 tokens | `MAX_MCP_OUTPUT_TOKENS=50000` |

For `MAX_MCP_OUTPUT_TOKENS`, Claude Code displays a warning when MCP tool output exceeds **10,000 tokens** and enforces a default maximum of 25,000 tokens, which can be increased via the environment variable.

Other relevant general-purpose environment variables include `BASH_DEFAULT_TIMEOUT_MS` and `BASH_MAX_TIMEOUT_MS` for bash command execution, but these do **not** apply to MCP tool calls.

## The MCP specification does not define timeout standards

The Model Context Protocol specification at **modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle** provides only high-level guidance: "Implementations SHOULD establish timeouts for all sent requests" and "SDKs and other middleware SHOULD allow these timeouts to be configured on a per-request basis." Critically, the specification **does not define**:

- Standard environment variable names for timeout configuration
- Specific default timeout values
- Timeout negotiation mechanisms between client and server
- Per-tool or per-resource timeout recommendations

A Specification Enhancement Proposal (SEP-1539) proposes formal timeout coordination including server-advertised timeout recommendations in capabilities, but this remains under review. Current SDK implementations vary: the TypeScript SDK defaults to 60 seconds, while Java and Python SDKs offer configurable timeouts through their APIs rather than environment variables.

## What this means for Alexandria RAG Server integration

For projects using MCP HTTP Streamable transport with Spring AI MCP SDK, the key implications are:

1. **Do not rely on MCP_TOOL_TIMEOUT**—it is not a functional configuration option despite appearing in community discussions
2. **MCP_TIMEOUT only affects server startup**, not the duration of individual tool calls once connected
3. **The 60-second hard cap** on MCP_TIMEOUT may cause issues with slow-starting MCP servers regardless of configured values
4. **Spring AI MCP SDK** provides its own timeout configuration via `spring.ai.mcp.client.request-timeout` property, which operates independently of Claude Code's environment variables and is the recommended approach for server-side timeout management
5. **Progress notifications** can reset timeout clocks in some implementations (per MCP spec guidance), but behavior varies across SDKs

## Conclusion

The documentation landscape for Claude Code MCP timeout configuration reveals a significant gap between user expectations and available functionality. While `MCP_TIMEOUT` is officially documented, its scope is limited to server startup and constrained by an unresolved 60-second cap bug. `MCP_TOOL_TIMEOUT` does not exist as a documented or functional feature—it is a community-anticipated variable that Claude Code currently ignores entirely. For production systems requiring configurable tool execution timeouts, the recommended approach is to implement timeout handling at the application layer or use SDK-specific configuration options rather than depending on undocumented environment variables.